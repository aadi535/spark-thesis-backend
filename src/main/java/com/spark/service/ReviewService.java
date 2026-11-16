package com.spark.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spark.dto.ReviewDtos.*;
import com.spark.dto.UserProgressDto.CompletedTaskDto;
import com.spark.model.ReviewAttempt;
import com.spark.repository.ReviewAttemptRepository;
import com.spark.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
public class ReviewService {
    private final UserService users;
    private final UserRepository accounts;
    private final ReviewAttemptRepository attempts;
    private final GroqAIService ai;
    private final AIContentValidator validator;
    private final ObjectMapper json;

    public ReviewService(UserService users, UserRepository accounts, ReviewAttemptRepository attempts,
                         GroqAIService ai, AIContentValidator validator, ObjectMapper json) {
        this.users = users;
        this.accounts = accounts;
        this.attempts = attempts;
        this.ai = ai;
        this.validator = validator;
        this.json = json;
    }

    private List<CompletedTaskDto> eligible(String email) {
        Map<String, CompletedTaskDto> unique = new LinkedHashMap<>();
        var history = users.getProgress(email).getCompletedTasks();
        if (history != null) for (var entry : history) {
            if (entry != null && "interception_explore".equals(entry.getCompletionSource())
                    && entry.getId() != null && !entry.getId().isBlank()
                    && entry.getTitle() != null && !entry.getTitle().isBlank()
                    && entry.getJournal() != null && entry.getJournal().trim().length() >= 40) {
                unique.putIfAbsent(entry.getId(), entry);
            }
        }
        return new ArrayList<>(unique.values());
    }

    // Provider generation deliberately runs outside a database transaction.
    public Started start(String email) {
        var sources = eligible(email);
        if (sources.size() < 5) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Explore at least five different ideas and wait for learning history to sync.");
        Collections.shuffle(sources);
        sources = sources.subList(0, 5);
        final List<GeneratedQuestion> generated;
        try {
            generated = ai.generateReview(sources);
            validator.validateReview(generated, sources.stream().map(CompletedTaskDto::getId)
                    .collect(java.util.stream.Collectors.toSet()));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Knowledge check unavailable. Please try again.");
        }
        Map<String, String> titles = new HashMap<>();
        sources.forEach(source -> titles.put(source.getId(), source.getTitle()));
        List<StoredQuestion> questions = new ArrayList<>();
        for (var question : generated) {
            List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
            Collections.shuffle(order);
            questions.add(new StoredQuestion(question.sourceEntryId(), titles.get(question.sourceEntryId()),
                    question.question().trim(), order.stream().map(i -> question.options().get(i).trim()).toList(),
                    order.indexOf(question.correctOptionIndex()), question.explanation().trim()));
        }
        Collections.shuffle(questions);
        var attempt = new ReviewAttempt();
        attempt.setUser(accounts.findByEmail(email).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again.")));
        attempt.setQuestions(write(questions));
        attempt = attempts.save(attempt);
        return new Started(attempt.getId(), questions.stream().map(question -> new Question(question.sourceEntryId(),
                question.topic(), question.question(), question.options())).toList());
    }

    @Transactional
    public Result submit(String email, UUID id, Submission submission) {
        var attempt = attempts.findByIdAndUserEmail(id, email).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge check not found."));
        if (attempt.getCompletedAt() != null) return result(attempt);
        var answers = submission == null ? null : submission.answers();
        if (answers == null || answers.size() != 5 || answers.stream().anyMatch(i -> i == null || i < 0 || i > 3))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answer all five questions.");
        var questions = readQuestions(attempt);
        int correct = 0;
        for (int i = 0; i < questions.size(); i++) if (answers.get(i) == questions.get(i).correctOptionIndex()) correct++;
        attempt.setAnswers(write(answers));
        attempt.setCorrectCount(correct);
        attempt.setCompletedAt(Instant.now());
        attempts.save(attempt);
        return result(attempt);
    }

    public History history(String email) {
        int count = eligible(email).size();
        return new History(count, attempts.findTop20ByUserEmailAndCompletedAtNotNullOrderByCompletedAtDesc(email)
                .stream().map(this::result).toList());
    }

    private Result result(ReviewAttempt attempt) {
        var questions = readQuestions(attempt);
        List<Integer> answers;
        try { answers = json.readValue(attempt.getAnswers(), new TypeReference<>() {}); }
        catch (Exception e) { throw new IllegalStateException("Could not read knowledge check answers", e); }
        List<Feedback> feedback = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            var question = questions.get(i);
            feedback.add(new Feedback(question.topic(), question.question(), question.options(), answers.get(i),
                    question.correctOptionIndex(), question.explanation()));
        }
        return new Result(attempt.getId(), attempt.getCompletedAt(), attempt.getCorrectCount(),
                attempt.getTotalCount(), feedback);
    }

    private List<StoredQuestion> readQuestions(ReviewAttempt attempt) {
        try { return json.readValue(attempt.getQuestions(), new TypeReference<>() {}); }
        catch (Exception e) { throw new IllegalStateException("Could not read knowledge check questions", e); }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Could not save knowledge check", e); }
    }
}
