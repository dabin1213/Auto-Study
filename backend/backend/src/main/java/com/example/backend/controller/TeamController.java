package com.example.backend.controller;

import com.example.backend.StudyContent;
import com.example.backend.StudyContentRepository;
import com.example.backend.Team;
import com.example.backend.TeamContent;
import com.example.backend.TeamContentRepository;
import com.example.backend.TeamMember;
import com.example.backend.TeamMemberRepository;
import com.example.backend.TeamRepository;
import com.example.backend.User;
import com.example.backend.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@RestController
public class TeamController {

    // 헷갈리는 문자(0/O, 1/I) 제외한 6자리 초대코드
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamContentRepository teamContentRepository;
    private final StudyContentRepository studyContentRepository;
    private final UserRepository userRepository;

    public TeamController(TeamRepository teamRepository,
                          TeamMemberRepository teamMemberRepository,
                          TeamContentRepository teamContentRepository,
                          StudyContentRepository studyContentRepository,
                          UserRepository userRepository) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamContentRepository = teamContentRepository;
        this.studyContentRepository = studyContentRepository;
        this.userRepository = userRepository;
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다."));
    }

    private ResponseEntity<?> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("error", code, "message", message));
    }

    private String generateInviteCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (teamRepository.existsByInviteCode(code));
        return code;
    }

    private String nicknameOf(Long userId) {
        return userRepository.findById(userId).map(User::getNickname).orElse("알 수 없음");
    }

    // 팀 생성 (만든 사람은 자동으로 멤버가 됨)
    @PostMapping("/team")
    @Transactional
    public ResponseEntity<?> create(@RequestBody Map<String, String> request, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return unauthorized();

        String teamName = request.get("teamName");
        if (teamName == null || teamName.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "팀 이름을 입력해주세요."));
        }
        if (teamName.trim().length() > 100) {
            return ResponseEntity.status(400).body(Map.of("error", "팀 이름은 100자 이하여야 해요."));
        }

        String description = request.get("description");
        if (description != null && description.trim().length() > 255) {
            return ResponseEntity.status(400).body(Map.of("error", "팀 소개는 255자 이하여야 해요."));
        }

        Team team = new Team();
        team.setTeamName(teamName.trim());
        team.setDescription(description == null || description.isBlank() ? null : description.trim());
        team.setInviteCode(generateInviteCode());
        team.setCreatedAt(LocalDateTime.now());
        Team saved = teamRepository.save(team);

        TeamMember member = new TeamMember();
        member.setTeamId(saved.getTeamId());
        member.setUserId(userId);
        member.setJoinedAt(LocalDateTime.now());
        teamMemberRepository.save(member);

        return ResponseEntity.status(201).body(Map.of(
                "team_id", saved.getTeamId(),
                "invite_code", saved.getInviteCode()
        ));
    }

    // 초대코드로 팀 참여
    @PostMapping("/team/join")
    @Transactional
    public ResponseEntity<?> join(@RequestBody Map<String, String> request, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return unauthorized();

        String inviteCode = request.get("inviteCode");
        if (inviteCode == null || inviteCode.isBlank()) {
            return error(404, "TEAM_001", "유효하지 않은 초대 코드입니다.");
        }

        Optional<Team> optionalTeam = teamRepository.findByInviteCode(inviteCode.trim().toUpperCase());
        if (optionalTeam.isEmpty()) {
            return error(404, "TEAM_001", "유효하지 않은 초대 코드입니다.");
        }

        Team team = optionalTeam.get();
        if (!teamMemberRepository.existsByTeamIdAndUserId(team.getTeamId(), userId)) {
            TeamMember member = new TeamMember();
            member.setTeamId(team.getTeamId());
            member.setUserId(userId);
            member.setJoinedAt(LocalDateTime.now());
            teamMemberRepository.save(member);
        }

        return ResponseEntity.ok(Map.of(
                "team_id", team.getTeamId(),
                "team_name", team.getTeamName()
        ));
    }

    // 내가 속한 팀 목록
    @GetMapping("/team/list")
    public ResponseEntity<?> list(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return unauthorized();

        List<Map<String, Object>> teams = teamMemberRepository.findByUserId(userId).stream()
                .map(tm -> teamRepository.findById(tm.getTeamId()).orElse(null))
                .filter(Objects::nonNull)
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("teamId", t.getTeamId());
                    m.put("teamName", t.getTeamName());
                    m.put("description", t.getDescription());
                    m.put("inviteCode", t.getInviteCode());
                    m.put("memberCount", teamMemberRepository.countByTeamId(t.getTeamId()));
                    m.put("contentCount", teamContentRepository.countByTeamId(t.getTeamId()));
                    return m;
                })
                .toList();

        return ResponseEntity.ok(Map.of("teams", teams));
    }

    // 팀 공유 콘텐츠 + 멤버 조회 (팀 멤버만 가능)
    @GetMapping("/team/{id}/contents")
    public ResponseEntity<?> contents(@PathVariable Long id, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return unauthorized();

        Optional<Team> optionalTeam = teamRepository.findById(id);
        if (optionalTeam.isEmpty()) {
            return error(404, "TEAM_001", "존재하지 않는 팀입니다.");
        }
        if (!teamMemberRepository.existsByTeamIdAndUserId(id, userId)) {
            return error(403, "TEAM_002", "팀 멤버만 접근 가능합니다.");
        }
        Team team = optionalTeam.get();

        // 이 팀에 공유된 자료 (공유한 순서 최신순)
        List<Map<String, Object>> contents = teamContentRepository.findByTeamId(id).stream()
                .sorted(Comparator.comparing(TeamContent::getSharedAt,
                        Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())))
                .map(tc -> studyContentRepository.findById(tc.getContentId()).orElse(null))
                .filter(Objects::nonNull)
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("contentId", c.getContentId());
                    m.put("fileName", c.getFileName());
                    m.put("owner", nicknameOf(c.getUserId()));
                    m.put("mine", c.getUserId().equals(userId));
                    m.put("difficulty", c.getDifficulty());
                    m.put("createdAt", c.getCreatedAt() == null ? null : c.getCreatedAt().toString());
                    m.put("hasQuiz", c.getQuizData() != null && !c.getQuizData().isBlank());
                    return m;
                })
                .toList();

        List<Map<String, Object>> members = teamMemberRepository.findByTeamId(id).stream()
                .map(tm -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("userId", tm.getUserId());
                    m.put("nickname", nicknameOf(tm.getUserId()));
                    m.put("me", tm.getUserId().equals(userId));
                    return m;
                })
                .toList();

        return ResponseEntity.ok(Map.of(
                "team_name", team.getTeamName(),
                "invite_code", team.getInviteCode(),
                "contents", contents,
                "members", members
        ));
    }

    // 내 학습 자료를 팀에 공유 (같은 자료를 여러 팀에 공유할 수 있음)
    @PostMapping("/team/{id}/share")
    public ResponseEntity<?> share(@PathVariable Long id,
                                   @RequestBody Map<String, Long> request,
                                   HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return unauthorized();

        if (!teamMemberRepository.existsByTeamIdAndUserId(id, userId)) {
            return error(403, "TEAM_002", "팀 멤버만 접근 가능합니다.");
        }

        Long contentId = request.get("contentId");
        if (contentId == null) {
            return ResponseEntity.status(400).body(Map.of("error", "공유할 자료를 선택해주세요."));
        }

        Optional<StudyContent> optionalContent = studyContentRepository.findById(contentId);
        if (optionalContent.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "해당 학습 자료를 찾을 수 없습니다."));
        }

        StudyContent content = optionalContent.get();
        if (!content.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "본인 자료만 공유할 수 있습니다."));
        }

        // 이미 이 팀에 공유된 자료면 그대로 성공 처리 (중복 저장 안 함)
        if (!teamContentRepository.existsByTeamIdAndContentId(id, contentId)) {
            TeamContent tc = new TeamContent();
            tc.setTeamId(id);
            tc.setContentId(contentId);
            tc.setSharedAt(LocalDateTime.now());
            teamContentRepository.save(tc);
        }

        return ResponseEntity.ok(Map.of("content_id", contentId, "team_id", id));
    }

    // 팀 공유 해제 (내가 공유한 자료만, 그 팀에서만 빠짐)
    @DeleteMapping("/team/{id}/share/{contentId}")
    @Transactional
    public ResponseEntity<?> unshare(@PathVariable Long id,
                                     @PathVariable Long contentId,
                                     HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return unauthorized();

        if (!teamMemberRepository.existsByTeamIdAndUserId(id, userId)) {
            return error(403, "TEAM_002", "팀 멤버만 접근 가능합니다.");
        }

        Optional<StudyContent> optionalContent = studyContentRepository.findById(contentId);
        if (optionalContent.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "해당 학습 자료를 찾을 수 없습니다."));
        }
        if (!optionalContent.get().getUserId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "내가 공유한 자료만 공유 해제할 수 있습니다."));
        }

        teamContentRepository.deleteByTeamIdAndContentId(id, contentId);
        return ResponseEntity.ok(Map.of("content_id", contentId, "team_id", id));
    }
}