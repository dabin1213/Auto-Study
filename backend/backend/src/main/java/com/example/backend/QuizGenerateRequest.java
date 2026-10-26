package com.example.backend;

import java.util.List;

public class QuizGenerateRequest {

    private Long contentId;
    private String difficulty;          // 초급 / 중급 / 고급
    private String summaryLength;       // 짧게 / 중간 / 길게
    private List<String> questionTypes; // 객관식 / 단답형 / OX (복수 선택)
    private int count;

    public Long getContentId() { return contentId; }
    public void setContentId(Long contentId) { this.contentId = contentId; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getSummaryLength() { return summaryLength; }
    public void setSummaryLength(String summaryLength) { this.summaryLength = summaryLength; }

    public List<String> getQuestionTypes() { return questionTypes; }
    public void setQuestionTypes(List<String> questionTypes) { this.questionTypes = questionTypes; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}