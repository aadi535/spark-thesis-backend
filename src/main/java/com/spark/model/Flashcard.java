package com.spark.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "flashcards")
public class Flashcard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    private String category;

    // ── Spaced Repetition (SM-2) ──
    @Column(name = "next_review")
    private LocalDateTime nextReview = LocalDateTime.now();

    private double stability = 1.0;
    private double difficulty = 1.0;
    private int reps = 0;
    private int lapses = 0;
    private int state = 0; // 0=New, 1=Learning, 2=Review, 3=Relearning

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ── Constructors ──
    public Flashcard() {}

    // ── Getters & Setters ──
    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public LocalDateTime getNextReview() { return nextReview; }
    public void setNextReview(LocalDateTime nextReview) { this.nextReview = nextReview; }
    public double getStability() { return stability; }
    public void setStability(double stability) { this.stability = stability; }
    public double getDifficulty() { return difficulty; }
    public void setDifficulty(double difficulty) { this.difficulty = difficulty; }
    public int getReps() { return reps; }
    public void setReps(int reps) { this.reps = reps; }
    public int getLapses() { return lapses; }
    public void setLapses(int lapses) { this.lapses = lapses; }
    public int getState() { return state; }
    public void setState(int state) { this.state = state; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Flashcard f = new Flashcard();
        public Builder user(User user) { f.user = user; return this; }
        public Builder question(String q) { f.question = q; return this; }
        public Builder answer(String a) { f.answer = a; return this; }
        public Builder category(String c) { f.category = c; return this; }
        public Flashcard build() { return f; }
    }
}
