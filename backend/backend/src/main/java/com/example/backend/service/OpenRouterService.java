package com.example.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OpenRouterService {

    private final RestClient restClient;

    @Value("${openrouter.api.key}")
    private String apiKey;

    private static final Logger log = LoggerFactory.getLogger(OpenRouterService.class);
    private static final String PRIMARY_MODEL = "google/gemma-4-31b-it:free";
    private static final String FALLBACK_MODEL = "openrouter/free";
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final int MAX_RETRIES = 3;
    private static final int TIMEOUT_SECONDS = 60;   // 응답이 이 시간 안에 안 오면 무응답으로 처리

    public OpenRouterService() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(TIMEOUT_SECONDS));

        this.restClient = RestClient.builder()
                .baseUrl(API_URL)
                .requestFactory(factory)
                .build();
    }

    /**
     * 학습 자료로 요약 + 퀴즈 JSON 생성.
     * 난이도 / 요약 길이 / 문제 유형 / 문제 개수에 따라 프롬프트가 달라진다.
     * 메인 모델은 최대 3회 재시도 후 폴백 모델로 전환하고,
     * 무응답(타임아웃)·서버 오류가 있었는데 끝내 실패하면 AI_001(503)로 알린다.
     */
    public String generateQuiz(String studyContentText, String difficulty, String summaryLength,
                               List<String> questionTypes, int count) {
        String systemPrompt = buildSystemPrompt(difficulty, summaryLength, questionTypes, count);
        String userPrompt = buildUserPrompt(studyContentText);

        boolean noResponse = false;   // 무응답·서버 오류가 한 번이라도 있었는지

        // 1) 메인 모델: 무응답이나 일시적 오류(429 등) 대비 최대 3회 재시도
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return callModel(PRIMARY_MODEL, systemPrompt, userPrompt);
            } catch (Exception e) {
                if (isNoResponse(e)) noResponse = true;
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(2000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // 2) 폴백 모델
        try {
            return callModel(FALLBACK_MODEL, systemPrompt, userPrompt);
        } catch (Exception fallbackEx) {
            if (isNoResponse(fallbackEx)) noResponse = true;
            if (noResponse) {
                throw new AiServiceException("AI_001", "AI 서버가 응답하지 않습니다. 잠시 후 다시 시도해주세요.", fallbackEx);
            }
            throw new AiServiceException("AI_002", "AI 응답 처리에 실패했습니다.", fallbackEx);
        }
    }

    private boolean isNoResponse(Exception e) {
        return e instanceof AiServiceException a && "AI_001".equals(a.getErrorCode());
    }

    private boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private String callModel(String model, String systemPrompt, String userPrompt) {
        Map<String, Object> requestBody = buildRequestBody(model, systemPrompt, userPrompt);

        try {
            Map<String, Object> response = restClient.post()
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        String body = new String(res.getBody().readAllBytes());
                        log.info("4xx 에러 응답: " + body);
                        throw new AiServiceException("AI_003", "요청 처리 중 오류가 발생했습니다.", null);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        String body = new String(res.getBody().readAllBytes());
                        log.info("5xx 에러 응답: " + body);
                        throw new AiServiceException("AI_001", "AI 서버가 응답하지 않습니다.", null);
                    })
                    .body(Map.class);

            log.info("응답 모델: " + (response == null ? "없음" : response.get("model")));
            return extractContent(response);

        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            if (isTimeout(e)) {
                log.info("타임아웃: " + model + " 이(가) " + TIMEOUT_SECONDS + "초 안에 응답하지 않음");
                throw new AiServiceException("AI_001",
                        "AI 서버가 " + TIMEOUT_SECONDS + "초 안에 응답하지 않았습니다.", e);
            }
            throw new AiServiceException("AI_001", "OpenRouter 호출 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 모델별로 요청 바디를 다르게 구성.
     * 메인 모델은 response_format(JSON 강제)을 지원하지만, 폴백 라우터는 쓰지 않는다.
     */
    private Map<String, Object> buildRequestBody(String model, String systemPrompt, String userPrompt) {
        if (model.equals(PRIMARY_MODEL)) {
            return Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "response_format", Map.of("type", "json_object")
            );
        } else {
            return Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");
            return cleanJsonResponse(content);
        } catch (Exception e) {
            throw new AiServiceException("AI_002", "AI 응답 파싱에 실패했습니다.", e);
        }
    }

    /** ```json ... ``` 마크다운 코드블록으로 감싸 응답한 경우 제거 */
    private String cleanJsonResponse(String content) {
        return content
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*$", "")
                .trim();
    }

    // ---------------- 프롬프트 조립 ----------------

    private String buildSystemPrompt(String difficulty, String summaryLength,
                                     List<String> types, int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("너는 학습 자료를 기반으로 요약본과 퀴즈를 만드는 AI야.\n");
        sb.append("아래 규칙을 반드시 지켜:\n");
        sb.append("1. 요약과 문제의 근거는 반드시 입력된 학습 자료에서 가져올 것 (자료에 없는 사실을 지어내지 말 것)\n");
        sb.append("2. 반드시 JSON 형식으로만 응답할 것 (설명, 인사말, 마크다운 코드블록 없이 순수 JSON만)\n");
        sb.append("3. 한국어로 작성할 것\n");
        sb.append("4. 모든 문제에 해설(explanation)을 포함하고, 정답의 근거를 자료 내용으로 설명할 것\n\n");

        sb.append("[요약 규칙]\n").append(summaryRule(summaryLength)).append("\n\n");
        sb.append("[난이도 규칙: ").append(difficulty).append("]\n").append(difficultyRule(difficulty)).append("\n");

        int[] counts = splitCount(count, types.size());
        sb.append("[출제 개수]\n");
        sb.append("총 ").append(count).append("문제를 아래 유형별 개수대로 출제하고, 같은 유형끼리 묶어서 순서대로 배치할 것\n");
        for (int i = 0; i < types.size(); i++) {
            if (counts[i] > 0) {
                sb.append("- ").append(types.get(i)).append(": ").append(counts[i]).append("문제\n");
            }
        }

        sb.append("\n[문제 유형별 형식]\n");
        for (int i = 0; i < types.size(); i++) {
            if (counts[i] > 0) {
                sb.append(typeSchema(types.get(i))).append("\n");
            }
        }

        sb.append("[출력 형식]\n");
        sb.append("{\n  \"summary\": \"학습 자료 요약\",\n  \"questions\": [ 위 유형별 형식의 문제 객체들 ]\n}\n");
        return sb.toString();
    }

    private String buildUserPrompt(String studyContentText) {
        return "아래 학습 자료로 요약과 퀴즈를 만들어줘.\n\n[학습 자료]\n" + studyContentText;
    }

    // 총 개수를 유형 수로 나눔 (10문제, 3유형 → 4/3/3)
    private int[] splitCount(int count, int typeCount) {
        int[] result = new int[typeCount];
        int base = count / typeCount;
        int rest = count % typeCount;
        for (int i = 0; i < typeCount; i++) {
            result[i] = base + (i < rest ? 1 : 0);
        }
        return result;
    }

    private String summaryRule(String summaryLength) {
        return switch (summaryLength) {
            case "짧게" -> "핵심만 2~3문장으로 짧게 요약할 것";
            case "길게" -> "주요 개념, 세부 내용, 개념 간 관계까지 7~10문장으로 자세히 요약할 것";
            default -> "주요 개념과 흐름을 4~6문장으로 요약할 것";
        };
    }

    private String difficultyRule(String difficulty) {
        return switch (difficulty) {
            case "초급" -> """
                    - 자료에 그대로 나오는 용어, 정의, 사실을 묻는 기본 암기형 문제로 출제할 것
                    - 자료의 표현을 거의 그대로 사용하고, 한 문제에 개념 하나만 묻기
                    - 객관식 오답 보기는 정답과 구분하기 쉬운 편으로 구성할 것
                    """;
            case "고급" -> """
                    - 자료의 개념을 새로운 상황·사례에 적용하거나 여러 개념을 종합해야 풀 수 있는 응용·추론형 문제로 출제할 것
                    - 단순 암기만으로 풀리는 문제는 피하고, 계산·순서 추론·비교 판단을 활용할 것
                    - 객관식 오답 보기는 흔한 오개념이나 비슷한 개념으로 매우 그럴듯하게 구성할 것
                    - 문제의 상황은 새로 만들어도 되지만, 정답의 근거는 반드시 학습 자료 안에 있을 것
                    """;
            default -> """
                    - 개념의 의미, 특징, 차이점, 원인과 결과를 이해했는지 묻는 문제로 출제할 것
                    - 자료의 표현을 그대로 옮기지 말고 바꿔서 묻기
                    - 객관식 오답 보기는 헷갈릴 만큼 그럴듯하게 구성할 것
                    """;
        };
    }

    private String typeSchema(String type) {
        return switch (type) {
            case "단답형" -> """
                    - 단답형: 보기 없이 짧은 단어나 용어로 답하는 문제. 정답은 한두 단어 이내로 하고, 서술형 답은 금지
                      {"type":"단답형","question":"문제","answer":"정답","acceptable":["정답으로 인정할 다른 표기"],"explanation":"해설"}
                      acceptable에는 띄어쓰기·한글/영문 표기 등 정답으로 인정할 다른 표현을 넣고, 없으면 빈 배열로 둘 것
                    """;
            case "OX" -> """
                    - OX: 참/거짓을 판단하는 문장 하나를 제시하는 문제
                      {"type":"OX","question":"판단할 문장","answer":true,"explanation":"해설"}
                      answer는 문장이 맞으면 true, 틀리면 false. 참과 거짓 정답을 고르게 섞을 것
                    """;
            default -> """
                    - 객관식: 보기 4개 중 정답이 1개인 문제
                      {"type":"객관식","question":"문제","options":["보기1","보기2","보기3","보기4"],"answer":0,"explanation":"해설"}
                      answer는 정답 보기의 번호 (0부터 시작, 0~3)
                    """;
        };
    }
}