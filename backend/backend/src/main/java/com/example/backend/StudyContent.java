package com.example.backend;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "study_contents")
public class StudyContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long contentId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String fileName;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String extractedText;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String summary;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String quizData;

    private String difficulty;

    // 생성할 때 고른 요약 길이 (짧게/중간/길게)
    private String summaryLength;

    // 생성할 때 고른 문제 유형 (예: "객관식,OX")
    @Column(length = 100)
    private String questionTypes;

    private LocalDateTime createdAt;

    public Long getContentId() { return contentId; }
    public void setContentId(Long contentId) { this.contentId = contentId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getQuizData() { return quizData; }
    public void setQuizData(String quizData) { this.quizData = quizData; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getSummaryLength() { return summaryLength; }
    public void setSummaryLength(String summaryLength) { this.summaryLength = summaryLength; }

    public String getQuestionTypes() { return questionTypes; }
    public void setQuestionTypes(String questionTypes) { this.questionTypes = questionTypes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}