package com.example.backend;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_histories")
public class QuizHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long contentId;

    // 팀 공유 공간에서 푼 기록이면 그 팀의 ID (개인 학습이면 null)
    private Long teamId;

    @Column(nullable = false)
    private int correctCount;

    @Column(nullable = false)
    private int totalCount;

    @Column(nullable = false)
    private int score;

    // 틀린 문제 번호 (예: "2,5")
    @Column(length = 255)
    private String wrongNos;

    // (예전 기록용) 내가 고른 답 번호 (예: "0,2,1,3")
    @Column(length = 255)
    private String answers;

    // 내가 낸 답 전체, JSON 배열 문자열 (예: ["0","서울","1"])
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String answerData;

    // 풀 당시의 퀴즈 원본
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String quizSnapshot;

    private LocalDateTime createdAt;

    public Long getHistoryId() { return historyId; }
    public void setHistoryId(Long historyId) { this.historyId = historyId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getContentId() { return contentId; }
    public void setContentId(Long contentId) { this.contentId = contentId; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public int getCorrectCount() { return correctCount; }
    public void setCorrectCount(int correctCount) { this.correctCount = correctCount; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public String getWrongNos() { return wrongNos; }
    public void setWrongNos(String wrongNos) { this.wrongNos = wrongNos; }

    public String getAnswers() { return answers; }
    public void setAnswers(String answers) { this.answers = answers; }

    public String getAnswerData() { return answerData; }
    public void setAnswerData(String answerData) { this.answerData = answerData; }

    public String getQuizSnapshot() { return quizSnapshot; }
    public void setQuizSnapshot(String quizSnapshot) { this.quizSnapshot = quizSnapshot; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}