package com.example.backend.controller;

import com.example.backend.QuizGenerateRequest;
import com.example.backend.QuizHistoryRepository;
import com.example.backend.StudyContent;
import com.example.backend.StudyContentRepository;
import com.example.backend.TeamContent;
import com.example.backend.TeamContentRepository;
import com.example.backend.TeamMemberRepository;
import com.example.backend.service.AiServiceException;
import com.example.backend.service.OpenRouterService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;



@RestController
public class QuizController {

    private static final List<String> DIFFICULTIES = List.of("초급", "중급", "고급");
    private static final List<String> SUMMARY_LENGTHS = List.of("짧게", "중간", "길게");

    // 모델 컨텍스트 262K에서, 프롬프트와 출력(최대 32K)에 쓸 여유분을 뺀 입력 한도
    private static final int MAX_INPUT_TOKENS = 200_000;

    private final OpenRouterService openRouterService;
    private final StudyContentRepository studyContentRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamContentRepository teamContentRepository;
    private final QuizHistoryRepository quizHistoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QuizController(OpenRouterService openRouterService,
                           StudyContentRepository studyContentRepository,
                           TeamMemberRepository teamMemberRepository,
                           TeamContentRepository teamContentRepository,
                           QuizHistoryRepository quizHistoryRepository) {
        this.openRouterService = openRouterService;
        this.studyContentRepository = studyContentRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamContentRepository = teamContentRepository;
        this.quizHistoryRepository = quizHistoryRepository;
    }

    private ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.status(400).body(Map.of("error", message));
    }

    // 본인 자료이거나, 이 자료가 공유된 팀 중 하나의 멤버인지
    private boolean canView(StudyContent c, Long userId) {
        if (c.getUserId().equals(userId)) return true;
        return teamContentRepository.findByContentId(c.getContentId()).stream()
                .anyMatch(tc -> teamMemberRepository.existsByTeamIdAndUserId(tc.getTeamId(), userId));
    }

    // 대략적인 토큰 수 추정: 한글·한자·가나는 글자당 1토큰, 그 밖의 글자(영문·숫자·기호)는 4글자당 1토큰, 공백은 0
    private int estimateTokens(String text) {
        if (text == null) return 0;
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) continue;
            Character.UnicodeScript script = Character.UnicodeScript.of(ch);
            if (script == Character.UnicodeScript.HANGUL || script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA) {
                cjk++;
            } else {
                other++;
            }
        }
        return cjk + (other + 3) / 4;
    }

    @PostMapping("/quiz/generate")
    public ResponseEntity<?> generateQuiz(@RequestBody QuizGenerateRequest request, HttpSession session) {

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다."));
        }

        Optional<StudyContent> optionalContent = studyContentRepository.findById(request.getContentId());
        if (optionalContent.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "해당 학습 자료를 찾을 수 없습니다."));
        }

        StudyContent content = optionalContent.get();

        if (!content.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "접근 권한이 없습니다."));
        }

        // ---- 옵션 검증 (값이 없으면 기본값) ----
        String difficulty = request.getDifficulty();
        if (difficulty == null || difficulty.isBlank()) difficulty = "중급";
        if (!DIFFICULTIES.contains(difficulty)) {
            return badRequest("지원하지 않는 난이도입니다.");
        }

        String summaryLength = request.getSummaryLength();
        if (summaryLength == null || summaryLength.isBlank()) summaryLength = "중간";
        if (!SUMMARY_LENGTHS.contains(summaryLength)) {
            return badRequest("지원하지 않는 요약 길이입니다.");
        }

        List<String> types = new ArrayList<>();
        if (request.getQuestionTypes() != null) {
            for (String raw : request.getQuestionTypes()) {
                String t = normalizeType(raw);
                if (t == null) {
                    return badRequest("지원하지 않는 문제 유형입니다.");
                }
                if (!types.contains(t)) types.add(t);
            }
        }
        if (types.isEmpty()) types.add("객관식");

        int count = request.getCount();
        if (count < 1 || count > 50) {
            return badRequest("문제 개수는 1개 이상 50개 이하로 입력해주세요.");
        }
        if (count < types.size()) {
            return badRequest("문제 개수는 선택한 문제 유형 수(" + types.size() + "개) 이상이어야 해요.");
        }

        // ---- 컨텍스트 초과 검사 (AI_003, 400): 너무 긴 자료는 AI를 부르기 전에 막는다 ----
        int estimated = estimateTokens(content.getExtractedText());
        if (estimated > MAX_INPUT_TOKENS) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", "AI_003",
                    "message", String.format(
                            "문서 내용이 너무 많습니다. (약 %,d토큰 / 최대 %,d토큰) 자료를 나누어 올려주세요.",
                            estimated, MAX_INPUT_TOKENS)));
        }

        try {
            String aiResponse = openRouterService.generateQuiz(
                    content.getExtractedText(), difficulty, summaryLength, types, count);

            JsonNode root = objectMapper.readTree(aiResponse);
            String summary = root.path("summary").asText("").trim();
            ArrayNode questions = normalizeQuestions(root.get("questions"), types, count);

            if (questions.isEmpty()) {
                return ResponseEntity.status(500).body(Map.of(
                        "error", "AI_002",
                        "message", "AI가 올바른 형식의 문제를 만들지 못했어요. 다시 시도해주세요."
                ));
            }

            String quizData = questions.toString();
            String typesText = String.join(",", types);

            content.setSummary(summary);
            content.setQuizData(quizData);
            content.setDifficulty(difficulty);
            content.setSummaryLength(summaryLength);
            content.setQuestionTypes(typesText);
            studyContentRepository.save(content);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("content_id", content.getContentId());
            body.put("summary", summary);
            body.put("quiz_data", quizData);
            body.put("difficulty", difficulty);
            body.put("summary_length", summaryLength);
            body.put("question_types", typesText);
            return ResponseEntity.status(201).body(body);

        } catch (AiServiceException e) {
            int status = switch (e.getErrorCode()) {
                case "AI_001" -> 503;
                case "AI_002" -> 500;
                default -> 400;
            };
            return ResponseEntity.status(status).body(Map.of(
                    "error", e.getErrorCode(),
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "AI_002",
                    "message", "AI 응답 파싱에 실패했습니다."
            ));
        }
    }

    // 내 학습 자료 목록 (최신순). teamIds = 이 자료를 공유한 팀들
    @GetMapping("/quiz/list")
    public ResponseEntity<?> list(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다."));
        }

        List<Map<String, Object>> items = studyContentRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(StudyContent::getCreatedAt,
                        Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())))
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("contentId", c.getContentId());
                    m.put("fileName", c.getFileName());
                    m.put("difficulty", c.getDifficulty());
                    m.put("summaryLength", c.getSummaryLength());
                    m.put("questionTypes", c.getQuestionTypes());
                    m.put("teamIds", teamContentRepository.findByContentId(c.getContentId()).stream()
                            .map(TeamContent::getTeamId).toList());
                    m.put("createdAt", c.getCreatedAt() == null ? null : c.getCreatedAt().toString());
                    m.put("hasQuiz", c.getQuizData() != null && !c.getQuizData().isBlank());
                    return m;
                })
                .toList();

        return ResponseEntity.ok(Map.of("contents", items));
    }

    // 퀴즈 상세 조회 (본인 또는 공유된 팀의 멤버)
    @GetMapping("/quiz/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다."));
        }

        Optional<StudyContent> optionalContent = studyContentRepository.findById(id);
        if (optionalContent.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "해당 학습 자료를 찾을 수 없습니다."));
        }

        StudyContent c = optionalContent.get();
        if (!canView(c, userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "접근 권한이 없습니다."));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content_id", c.getContentId());
        body.put("file_name", c.getFileName());
        body.put("summary", c.getSummary() == null ? "" : c.getSummary());
        body.put("quiz_data", c.getQuizData() == null ? "[]" : c.getQuizData());
        body.put("difficulty", c.getDifficulty());
        body.put("summary_length", c.getSummaryLength());
        body.put("question_types", c.getQuestionTypes());
        return ResponseEntity.ok(body);
    }

    // 퀴즈(학습 자료) 삭제 - 본인 생성분만. 공유된 모든 팀에서도 함께 빠짐
    @DeleteMapping("/quiz/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다."));
        }

        Optional<StudyContent> optionalContent = studyContentRepository.findById(id);
        if (optionalContent.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "해당 학습 자료를 찾을 수 없습니다."));
        }

        StudyContent c = optionalContent.get();
        if (!c.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "본인이 만든 자료만 삭제할 수 있습니다."));
        }

        // 이 자료의 팀 공유 정보와, 이 자료에 남긴 내 풀이 기록도 함께 삭제
        teamContentRepository.deleteByContentId(id);
        quizHistoryRepository.deleteByContentIdAndUserId(id, userId);
        studyContentRepository.delete(c);

        return ResponseEntity.ok(Map.of("deleted", id));
    }

    // ---------------- AI 응답 형식 검사 ----------------

    // "객관식" / "단답형" / "OX"(O/X 표기 포함)로 통일. 모르는 값이면 null
    private String normalizeType(String raw) {
        if (raw == null) return null;
        String t = raw.replaceAll("\\s", "").toUpperCase();
        if (t.contains("객관")) return "객관식";
        if (t.contains("단답")) return "단답형";
        if (t.contains("OX") || t.contains("O/X")) return "OX";
        return null;
    }

    // 요청한 유형에 맞고 형식이 올바른 문제만 남기고, 최대 count개까지 자름
    private ArrayNode normalizeQuestions(JsonNode raw, List<String> types, int count) {
        ArrayNode result = objectMapper.createArrayNode();
        if (raw == null || !raw.isArray()) return result;
        for (JsonNode q : raw) {
            if (result.size() >= count) break;
            ObjectNode n = normalizeQuestion(q, types);
            if (n != null) result.add(n);
        }
        return result;
    }

    private ObjectNode normalizeQuestion(JsonNode q, List<String> types) {
        String question = q.path("question").asText("").trim();
        if (question.isEmpty()) return null;
        String explanation = q.path("explanation").asText("").trim();
        JsonNode answer = q.path("answer");

        String type = normalizeType(q.path("type").asText(""));
        if (type == null) {   // type 표기가 없으면 모양으로 추정
            if (q.path("options").isArray() && q.path("options").size() == 4) type = "객관식";
            else if (answer.isBoolean()) type = "OX";
            else if (answer.isTextual()) type = "단답형";
            else return null;
        }
        if (!types.contains(type)) return null;

        ObjectNode n = objectMapper.createObjectNode();
        n.put("type", type);
        n.put("question", question);

        switch (type) {
            case "객관식": {
                JsonNode opts = q.path("options");
                if (!opts.isArray() || opts.size() != 4) return null;
                ArrayNode options = n.putArray("options");
                for (JsonNode o : opts) {
                    String s = o.asText("").trim();
                    if (s.isEmpty()) return null;
                    options.add(s);
                }
                int idx = answerIndex(answer, options);
                if (idx < 0 || idx > 3) return null;
                n.put("answer", idx);
                break;
            }
            case "OX": {
                int idx = oxIndex(answer);
                if (idx < 0) return null;
                n.putArray("options").add("O").add("X");
                n.put("answer", idx);   // 0 = O, 1 = X
                break;
            }
            default: {   // 단답형
                String text = answer.asText("").trim();
                if (text.isEmpty()) return null;
                n.put("answer", text);
                ArrayNode acceptable = n.putArray("acceptable");
                JsonNode accRaw = q.path("acceptable");
                if (accRaw.isArray()) {
                    for (JsonNode a : accRaw) {
                        String s = a.asText("").trim();
                        if (!s.isEmpty()) acceptable.add(s);
                    }
                }
            }
        }
        n.put("explanation", explanation);
        return n;
    }

    private int answerIndex(JsonNode answer, ArrayNode options) {
        if (answer.isIntegralNumber()) return answer.asInt(-1);
        if (answer.isTextual()) {
            String s = answer.asText().trim();
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                // 번호가 아니라 보기 문장으로 답한 경우
            }
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).asText().equals(s)) return i;
            }
        }
        return -1;
    }

    private int oxIndex(JsonNode answer) {
        if (answer.isBoolean()) return answer.asBoolean() ? 0 : 1;
        if (answer.isTextual()) {
            String s = answer.asText().trim().toUpperCase();
            if (s.equals("O") || s.equals("TRUE") || s.equals("참") || s.equals("맞다") || s.equals("맞음")) return 0;
            if (s.equals("X") || s.equals("FALSE") || s.equals("거짓") || s.equals("틀리다") || s.equals("틀림")) return 1;
        }
        return -1;
    }
}