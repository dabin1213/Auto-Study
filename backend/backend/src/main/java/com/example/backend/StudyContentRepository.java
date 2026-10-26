package com.example.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StudyContentRepository extends JpaRepository<StudyContent, Long> {
    List<StudyContent> findByUserId(Long userId);
}