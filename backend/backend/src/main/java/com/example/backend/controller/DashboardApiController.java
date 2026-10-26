package com.example.backend.controller;

import com.example.backend.QuizHistory;
import com.example.backend.QuizHistoryRepository;
import com.example.backend.StudyContent;
import com.example.backend.StudyContentRepository;
import com.example.backend.TeamMemberRepository;
import com.example.backend.TeamRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
public class DashboardApiController {

    private final StudyContentRepository studyContentRepository;
    private final QuizHistoryRepository quizHistoryRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;

    public DashboardApiController(StudyContentRepository studyContentRepository,
                                  QuizHistoryRepository quizHistoryRepository,
                                  TeamMemberRepository teamMemberRepository,
                                  TeamRepository teamRepository) {
        this.studyContentRepository = studyContentRepository;
        this.quizHistoryRepository = quizHistoryRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
    }

    // 홈 대시보드: 통계 카드 + 최근 학습 + 그래프 데이터 + 소속 팀
    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다."));
        }

        // 퀴즈를 생성한 내 자료 (최신순)
        List<StudyContent> contents = studyContentRepository.findByUserId(userId).stream()
                .filter(c -> c.getQuizData() != null && !c.getQuizData().isBlank())
                .sorted(Comparator.comparing(StudyContent::getCreatedAt,
                        Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())))
                .toList();

        // 내 풀이 기록 (최신순)
        List<QuizHistory> histories = quizHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);

        int avgScore = histories.isEmpty() ? 0
                : (int) Math.round(histories.stream().mapToInt(QuizHistory::getScore).average().orElse(0));

        // 자료별 가장 최근 풀이 점수 (기록이 최신순이라 처음 만나는 게 가장 최근)
        Map<Long, Integer> latestScore = new HashMap<>();
        for (QuizHistory h : histories) {
            latestScore.putIfAbsent(h.getContentId(), h.getScore());
        }

        // 최근 학습 5건
        List<Map<String, Object>> recent = contents.stream().limit(5).map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            Integer score = latestScore.get(c.getContentId());
            m.put("contentId", c.getContentId());
            m.put("fileName", c.getFileName());
            m.put("difficulty", c.getDifficulty());
            m.put("createdAt", c.getCreatedAt() == null ? null : c.getCreatedAt().toString());
            m.put("score", score);   // 아직 안 풀었으면 null
            m.put("status", score == null ? "미풀이" : "완료");
            return m;
        }).toList();

        // 그래프 1: 최근 점수 추이 (최근 10회, 오래된 순)
        List<Map<String, Object>> scoreTrend = new ArrayList<>();
        for (int i = Math.min(10, histories.size()) - 1; i >= 0; i--) {
            QuizHistory h = histories.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("score", h.getScore());
            m.put("date", h.getCreatedAt() == null ? null : h.getCreatedAt().toLocalDate().toString());
            m.put("fileName", studyContentRepository.findById(h.getContentId())
                    .map(StudyContent::getFileName).orElse("삭제된 자료"));
            scoreTrend.add(m);
        }

        // 그래프 2: 최근 7일 일별 푼 횟수 (기록이 없는 날은 0)
        Map<LocalDate, Long> perDay = histories.stream()
                .filter(h -> h.getCreatedAt() != null)
                .collect(Collectors.groupingBy(h -> h.getCreatedAt().toLocalDate(), Collectors.counting()));
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> daily = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d.toString());
            m.put("count", perDay.getOrDefault(d, 0L));
            daily.add(m);
        }

        // 소속 팀 (팀 이름 + 팀별 멤버 수 집계)
        List<Map<String, Object>> teams = teamMemberRepository.findByUserId(userId).stream()
                .map(tm -> teamRepository.findById(tm.getTeamId()).orElse(null))
                .filter(Objects::nonNull)
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("teamId", t.getTeamId());
                    m.put("teamName", t.getTeamName());
                    m.put("memberCount", teamMemberRepository.countByTeamId(t.getTeamId()));
                    return m;
                })
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("quiz_count", contents.size());
        body.put("solved_count", histories.size());
        body.put("avg_score", avgScore);
        body.put("team_count", teamMemberRepository.countByUserId(userId));
        body.put("recent", recent);
        body.put("score_trend", scoreTrend);
        body.put("daily", daily);
        body.put("teams", teams);
        return ResponseEntity.ok(body);
    }
}