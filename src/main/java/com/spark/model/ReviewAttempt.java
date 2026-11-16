package com.spark.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_attempts")
public class ReviewAttempt {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    private Instant completedAt;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String questions;
    @Column(columnDefinition = "TEXT")
    private String answers;
    private Integer correctCount;
    private int totalCount = 5;

    public UUID getId() { return id; }
    public void setUser(User user) { this.user = user; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getQuestions() { return questions; }
    public void setQuestions(String questions) { this.questions = questions; }
    public String getAnswers() { return answers; }
    public void setAnswers(String answers) { this.answers = answers; }
    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(int count) { correctCount = count; }
    public int getTotalCount() { return totalCount; }
}
