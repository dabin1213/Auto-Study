package com.example.backend;

import java.util.List;

public class HistoryRequest {

    private Long contentId;
    // 객관식·OX는 고른 보기 번호("0","1"...), 단답형은 입력한 글자
    private List<String> answers;
    private Long teamId;   // 팀 공유 공간에서 풀었을 때만 값이 들어옴

    public Long getContentId() { return contentId; }
    public void setContentId(Long contentId) { this.contentId = contentId; }

    public List<String> getAnswers() { return answers; }
    public void setAnswers(List<String> answers) { this.answers = answers; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
}