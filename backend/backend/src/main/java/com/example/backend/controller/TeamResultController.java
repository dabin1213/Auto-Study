package com.example.backend.controller;

import com.example.backend.QuizHistory;
import com.example.backend.QuizHistoryRepository;
import com.example.backend.TeamContent;
import com.example.backend.TeamContentRepository;
import com.example.backend.TeamMemberRepository;
import com.example.backend.User;
import com.example.backend.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class TeamResultController {

    private record MemberStat(Long userId, String nickname, boolean me,
                              int solvedCount, Integer avgScore, Integer bestScore) {}

    private final TeamMemberRepository teamMemberRepository;
    private final TeamContentRepository teamContentRepository;
    private final QuizHistoryRepository quizHistoryRepository;
    private final UserRepository userRepository;

    public TeamResultController(TeamMemberRepository teamMemberRepository,
                                TeamContentRepository teamContentRepository,
                                QuizHistoryRepository quizHistoryRepository,
                                UserRepository userRepository) {
        this.teamMemberRepository = teamMemberRepository;
        this.teamContentRepository = teamContentRepository;
        this.quizHistoryRepository = quizHistoryRepository;
        this.userRepository = userRepository;
    }

    private String nicknameOf(Long userId) {
        return userRepository.findById(userId).map(User::getNickname).orElse("알 수 없음");
    }

    // 팀 퀴즈 결과 공유: 멤버별 점수(순위)와 자료별 풀이 현황 (팀 멤버만 조회 가능)
    @GetMapping("/team/{id}/results")
    public ResponseEntity<?> results(@PathVariable Long id, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다."));
        }
        if (!teamMemberRepository.existsByTeamIdAndUserId(id, userId)) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "TEAM_002", "message", "팀 멤버만 접근 가능합니다."));
        }

        // 이 팀에 지금 공유 중인 자료
        Set<Long> sharedIds = teamContentRepository.findByTeamId(id).stream()
                .map(TeamContent::getContentId)
                .collect(Collectors.toSet());

        // 이 팀 공유 공간에서 푼 기록 중, 지금도 공유 중인 자료의 것만
        List<QuizHistory> histories = quizHistoryRepository.findByTeamId(id).stream()
                .filter(h -> sharedIds.contains(h.getContentId()))
                .toList();

        // 멤버별 집계 (평균 점수 높은 순, 안 푼 사람은 맨 뒤)
        List<MemberStat> stats = teamMemberRepository.findByTeamId(id).stream()
                .map(tm -> {
                    List<QuizHistory> mine = histories.stream()
                            .filter(h -> h.getUserId().equals(tm.getUserId()))
                            .toList();
                    Integer avg = mine.isEmpty() ? null
                            : (int) Math.round(mine.stream().mapToInt(QuizHistory::getScore).average().orElse(0));
                    Integer best = mine.isEmpty() ? null
                            : mine.stream().mapToInt(QuizHistory::getScore).max().orElse(0);
                    return new MemberStat(tm.getUserId(), nicknameOf(tm.getUserId()),
                            tm.getUserId().equals(userId), mine.size(), avg, best);
                })
                .sorted(Comparator.comparing(MemberStat::avgScore,
                                Comparator.nullsLast(Comparator.<Integer>reverseOrder()))
                        .thenComparing(MemberStat::nickname))
                .toList();

        List<Map<String, Object>> members = new ArrayList<>();
        int rank = 0;
        for (MemberStat s : stats) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", s.userId());
            m.put("nickname", s.nickname());
            m.put("me", s.me());
            m.put("solvedCount", s.solvedCount());
            m.put("avgScore", s.avgScore());
            m.put("bestScore", s.bestScore());
            m.put("rank", s.avgScore() == null ? null : ++rank);
            members.add(m);
        }

        // 자료별 풀이 현황 (푼 기록이 있는 자료만)
        Map<Long, List<QuizHistory>> byContent = histories.stream()
                .collect(Collectors.groupingBy(QuizHistory::getContentId));

        List<Map<String, Object>> contents = byContent.entrySet().stream()
                .map(e -> {
                    List<QuizHistory> hs = e.getValue();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("contentId", e.getKey());
                    m.put("solverCount", hs.stream().map(QuizHistory::getUserId).distinct().count());
                    m.put("attemptCount", hs.size());
                    m.put("avgScore", (int) Math.round(
                            hs.stream().mapToInt(QuizHistory::getScore).average().orElse(0)));
                    return m;
                })
                .toList();

        return ResponseEntity.ok(Map.of("members", members, "contents", contents));
    }
}