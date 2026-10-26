package com.example.backend.controller;

import com.example.backend.HistoryRequest;
import com.example.backend.QuizHistory;
import com.example.backend.QuizHistoryRepository;
import com.example.backend.StudyContent;
import com.example.backend.StudyContentRepository;
import com.example.backend.Team;
import com.example.backend.TeamContentRepository;
import com.example.backend.TeamMemberRepository;
import com.example.backend.TeamRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class HistoryController {

    private final QuizHistoryRepository quizHistoryRepository;
    private final StudyContentRepository studyContentRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamContentRepository teamContentRepository;
    private final TeamRepository teamRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HistoryController(QuizHistoryRepository quizHistoryRepository,
                             StudyContentRepository studyContentRepository,
                             TeamMemberRepository teamMemberRepository,
                             TeamContentRepository teamContentRepository,
                             TeamRepository teamRepository) {
        this.quizHistoryRepository = quizHistoryRepository;
        this.studyContentRepository = studyContentRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamContentRepository = teamContentRepository;
        this.teamRepository = teamRepository;
    }

    // 풀이 기록 저장 (서버가 채점)
    @PostMapping("/history")
    public ResponseEntity<?> save(@RequestBody HistoryRequest request, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다."));
        }

        List<String> rawAnswers = request.getAnswers();
        if (request.getContentId() == null || rawAnswers == null || rawAnswers.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("error", "잘못된 요청입니다."));
        }
        // null은 빈 문자열로, 너무 긴 입력은 200자까지만
        List<String> answers = rawAnswers.stream()
                .map(a -> a == null ? "" : (a.length() > 200 ? a.substring(0, 200) : a))
                .toList();

        Optional<StudyContent> optionalContent = studyContentRepository.findById(request.getContentId());
        if (optionalContent.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "해당 학습 자료를 찾을 수 없습니다."));
        }

        // 본인 자료이거나, 이 자료가 공유된 팀 중 하나의 멤버만 기록 가능
        StudyContent content = optionalContent.get();
        boolean isOwner = content.getUserId().equals(userId);
        boolean isTeamMember = teamContentRepository.findByContentId(content.getContentId()).stream()
                .anyMatch(tc -> teamMemberRepository.existsByTeamIdAndUserId(tc.getTeamId(), userId));
        if (!isOwner && !isTeamMember) {
            return ResponseEntity.status(403).body(Map.of("error", "접근 권한이 없습니다."));
        }

        String quizData = content.getQuizData();
        if (quizData == null || quizData.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "퀴즈가 아직 생성되지 않은 자료입니다."));
        }

        JsonNode quiz;
        try {
            quiz = objectMapper.readTree(quizData);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "퀴즈 데이터를 읽을 수 없습니다."));
        }
        if (!quiz.isArray() || quiz.size() != answers.size()) {
            return ResponseEntity.status(400).body(Map.of("error", "답안 개수가 문제 수와 맞지 않습니다."));
        }

        // 서버 채점
        List<Boolean> results = grade(quiz, answers);
        int total = quiz.size();
        int correct = 0;
        List<Integer> wrongNos = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            if (results.get(i)) correct++;
            else wrongNos.add(i + 1);
        }

        // 팀 공유 공간에서 푼 기록이면 팀 표시.
        // 내가 그 팀 멤버이고, 자료가 실제로 그 팀에 공유돼 있을 때만 (아니면 개인 기록으로 저장)
        Long teamId = request.getTeamId();
        if (teamId != null && !(teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)
                && teamContentRepository.existsByTeamIdAndContentId(teamId, request.getContentId()))) {
            teamId = null;
        }

        QuizHistory history = new QuizHistory();
        history.setUserId(userId);
        history.setContentId(request.getContentId());
        history.setTeamId(teamId);
        history.setCorrectCount(correct);
        history.setTotalCount(total);
        history.setScore((int) Math.round(correct * 100.0 / total));
        history.setWrongNos(wrongNos.stream().map(String::valueOf).collect(Collectors.joining(",")));
        history.setAnswerData(toJson(answers));
        history.setQuizSnapshot(quizData);
        history.setCreatedAt(LocalDateTime.now());
        QuizHistory saved = quizHistoryRepository.save(history);

        int streak = calcStreak(quizHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId));

        return ResponseEntity.status(201).body(Map.of(
                "history_id", saved.getHistoryId(),
                "score", saved.getScore(),
                "correct_count", correct,
                "total_count", total,
                "streak_days", streak
        ));
    }

    // 풀이 기록 조회 (최신순) + 평균 점수 + 연속 학습일
    @GetMapping("/history/list")
    public ResponseEntity<?> list(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다."));
        }

        List<QuizHistory> histories = quizHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);

        List<Map<String, Object>> records = histories.stream()
                .map(h -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("historyId", h.getHistoryId());
                    m.put("contentId", h.getContentId());
                    m.put("fileName", studyContentRepository.findById(h.getContentId())
                            .map(StudyContent::getFileName).orElse("삭제된 자료"));
                    m.put("teamId", h.getTeamId());
                    m.put("teamName", teamNameOf(h.getTeamId()));
                    m.put("score", h.getScore());
                    m.put("correctCount", h.getCorrectCount());
                    m.put("totalCount", h.getTotalCount());
                    m.put("wrongNos", parseNumbers(h.getWrongNos()));
                    m.put("reviewable", h.getQuizSnapshot() != null && !h.getQuizSnapshot().isBlank()
                            && !answersOf(h).isEmpty());
                    m.put("createdAt", h.getCreatedAt() == null ? null : h.getCreatedAt().toString());
                    return m;
                })
                .toList();

        int avgScore = histories.isEmpty() ? 0
                : (int) Math.round(histories.stream().mapToInt(QuizHistory::getScore).average().orElse(0));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("records", records);
        body.put("avg_score", avgScore);
        body.put("streak_days", calcStreak(histories));
        body.put("total_count", histories.size());
        return ResponseEntity.ok(body);
    }

    // 풀이 다시보기: 풀 당시의 문제 + 내가 낸 답 + 문제별 정오 (본인 기록만)
    @GetMapping("/history/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다."));
        }

        Optional<QuizHistory> optionalHistory = quizHistoryRepository.findById(id);
        if (optionalHistory.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "해당 풀이 기록을 찾을 수 없습니다."));
        }

        QuizHistory h = optionalHistory.get();
        if (!h.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "접근 권한이 없습니다."));
        }

        List<String> answers = answersOf(h);
        if (h.getQuizSnapshot() == null || h.getQuizSnapshot().isBlank() || answers.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "이 기록은 문제 다시보기를 지원하지 않아요."));
        }

        JsonNode quiz;
        try {
            quiz = objectMapper.readTree(h.getQuizSnapshot());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "퀴즈 데이터를 읽을 수 없습니다."));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("history_id", h.getHistoryId());
        body.put("file_name", studyContentRepository.findById(h.getContentId())
                .map(StudyContent::getFileName).orElse("삭제된 자료"));
        body.put("team_name", teamNameOf(h.getTeamId()));
        body.put("score", h.getScore());
        body.put("correct_count", h.getCorrectCount());
        body.put("total_count", h.getTotalCount());
        body.put("created_at", h.getCreatedAt() == null ? null : h.getCreatedAt().toString());
        body.put("quiz_data", h.getQuizSnapshot());
        body.put("answers", answers);
        body.put("results", grade(quiz, answers));
        return ResponseEntity.ok(body);
    }

    // 풀이 기록 삭제 (본인 기록만)
    @DeleteMapping("/history/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다."));
        }

        Optional<QuizHistory> optionalHistory = quizHistoryRepository.findById(id);
        if (optionalHistory.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "해당 풀이 기록을 찾을 수 없습니다."));
        }

        QuizHistory h = optionalHistory.get();
        if (!h.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "본인 기록만 삭제할 수 있습니다."));
        }

        quizHistoryRepository.delete(h);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    // ---------------- 채점 ----------------

    // 문제별 정답 여부. type이 없는 예전 퀴즈는 객관식으로 처리
    private List<Boolean> grade(JsonNode quiz, List<String> answers) {
        List<Boolean> results = new ArrayList<>();
        if (!quiz.isArray()) return results;
        for (int i = 0; i < quiz.size(); i++) {
            JsonNode q = quiz.get(i);
            String given = (i < answers.size() && answers.get(i) != null) ? answers.get(i) : "";
            String type = q.path("type").asText("객관식");
            results.add("단답형".equals(type) ? isShortCorrect(q, given) : isChoiceCorrect(q, given));
        }
        return results;
    }

    // 객관식·OX: 고른 보기 번호가 정답 번호와 같은지
    private boolean isChoiceCorrect(JsonNode q, String given) {
        int right = q.path("answer").asInt(-1);
        try {
            return right >= 0 && Integer.parseInt(given.trim()) == right;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // 단답형: 띄어쓰기·대소문자·특수문자를 무시하고 정답 또는 인정 답과 같은지
    private boolean isShortCorrect(JsonNode q, String given) {
        String g = normalize(given);
        if (g.isEmpty()) return false;
        if (g.equals(normalize(q.path("answer").asText("")))) return true;
        for (JsonNode a : q.path("acceptable")) {
            if (g.equals(normalize(a.asText("")))) return true;
        }
        return false;
    }

    // quiz-result.js의 normalizeAnswer와 같은 규칙
    private String normalize(String s) {
        return s.toLowerCase().replaceAll("[\\s\\p{Punct}]", "");
    }

    // ---------------- 유틸 ----------------

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    // 새 기록은 answerData(JSON), 예전 기록은 answers("0,2,1")에서 꺼냄
    private List<String> answersOf(QuizHistory h) {
        if (h.getAnswerData() != null && !h.getAnswerData().isBlank()) {
            try {
                return objectMapper.readValue(h.getAnswerData(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                return List.of();
            }
        }
        if (h.getAnswers() != null && !h.getAnswers().isBlank()) {
            return Arrays.stream(h.getAnswers().split(",")).map(String::trim).toList();
        }
        return List.of();
    }

    private String teamNameOf(Long teamId) {
        if (teamId == null) return null;
        return teamRepository.findById(teamId).map(Team::getTeamName).orElse("팀");
    }

    private List<Integer> parseNumbers(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split(",")).map(String::trim).map(Integer::parseInt).toList();
    }

    // 오늘(없으면 어제)부터 거꾸로 하루도 안 빠지고 푼 날 수
    private int calcStreak(List<QuizHistory> histories) {
        Set<LocalDate> days = histories.stream()
                .map(QuizHistory::getCreatedAt)
                .filter(t -> t != null)
                .map(LocalDateTime::toLocalDate)
                .collect(Collectors.toSet());

        LocalDate day = LocalDate.now();
        if (!days.contains(day)) {
            day = day.minusDays(1);
        }
        int streak = 0;
        while (days.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }
}