package com.example.backend;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// 어떤 자료를 어떤 팀에 공유했는지 (한 자료를 여러 팀에 공유할 수 있음)
@Entity
@Table(name = "team_contents",
       uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "content_id"}))
public class TeamContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long teamId;

    @Column(nullable = false)
    private Long contentId;

    private LocalDateTime sharedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public Long getContentId() { return contentId; }
    public void setContentId(Long contentId) { this.contentId = contentId; }

    public LocalDateTime getSharedAt() { return sharedAt; }
    public void setSharedAt(LocalDateTime sharedAt) { this.sharedAt = sharedAt; }
}