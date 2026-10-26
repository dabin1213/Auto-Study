package com.example.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuizHistoryRepository extends JpaRepository<QuizHistory, Long> {
    List<QuizHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<QuizHistory> findByTeamId(Long teamId);
    void deleteByContentIdAndUserId(Long contentId, Long userId);
}