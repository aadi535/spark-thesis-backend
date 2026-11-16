package com.spark.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReviewDtos {
    private ReviewDtos() {}

    public record GeneratedQuestion(String sourceEntryId, String question, List<String> options,
                                    Integer correctOptionIndex, String explanation) {}
    public record StoredQuestion(String sourceEntryId, String topic, String question, List<String> options,
                                 int correctOptionIndex, String explanation) {}
    public record Question(String sourceEntryId, String topic, String question, List<String> options) {}
    public record Started(UUID id, List<Question> questions) {}
    public record Submission(List<Integer> answers) {}
    public record Feedback(String topic, String question, List<String> options, int selectedOptionIndex,
                           int correctOptionIndex, String explanation) {}
    public record Result(UUID id, Instant completedAt, int correctCount, int totalCount, List<Feedback> questions) {}
    public record History(int eligibleCount, List<Result> results) {}
}
