package com.example.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TeamContentRepository extends JpaRepository<TeamContent, Long> {
    List<TeamContent> findByTeamId(Long teamId);
    List<TeamContent> findByContentId(Long contentId);
    boolean existsByTeamIdAndContentId(Long teamId, Long contentId);
    long countByTeamId(Long teamId);
    void deleteByContentId(Long contentId);
    void deleteByTeamIdAndContentId(Long teamId, Long contentId);
}