package com.example.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    List<TeamMember> findByUserId(Long userId);
    List<TeamMember> findByTeamId(Long teamId);
    boolean existsByTeamIdAndUserId(Long teamId, Long userId);

    // 집계: 내가 속한 팀 수 / 팀별 멤버 수
    long countByUserId(Long userId);
    long countByTeamId(Long teamId);
}