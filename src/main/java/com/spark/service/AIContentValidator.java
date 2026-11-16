package com.spark.service;

import com.spark.dto.QuestionCardDto;
import com.spark.dto.SparkRequest;
import com.spark.dto.SparkTaskDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class AIContentValidator {
    private static final Logger log = LoggerFactory.getLogger(AIContentValidator.class);
    public static final String CATEGORIES = "Science, Technology, Space, History, Psychology, Nature, Mystery, Society, Health, Economics, Mathematics, Philosophy";
    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "science", "technology", "space", "history", "psychology", "nature", "mystery",
            "society", "health", "economics", "mathematics", "philosophy",
            "physics", "chemistry", "biology", "astronomy", "astrophysics", "earth", "weather",
            "animals", "oceans", "everyday phenomena", "science & cosmos", "astrophysics & cosmos",
            "psychology & mind", "tech & ai", "biology & nature", "biology & genetics",
            "ancient history", "mathematics & logic", "philosophy schools", "discovery stories", "deep discovery");
    private static final Pattern UNSAFE = Pattern.compile(
            "\\b(pornography|pornographic|sexually explicit|erotic (story|stories)|graphic sexual|nude photos)\\b"
            + "|\\b(gory details|graphic dismemberment|graphic descriptions? of torture)\\b"
            + "|\\b(how to|instructions (for|to)) (kill|hang|cut|poison) (yourself|myself)\\b"
            + "|\\b(you should|you must|go and) (kill|hang|cut|poison) yourself\\b"
            + "|\\b(step[ -]by[ -]step|instructions (for|to))[^.!?]{0,60}\\b(make|build|assemble) (a |an )?(bomb|explosive|weapon)\\b"
            + "|\\b(how to|instructions (for|to)) (steal passwords|commit fraud|(make|build|assemble) (a |an )?(bomb|explosive)|manufacture methamphetamine)\\b"
            + "|\\b(all|those) (jews|muslims|christians|immigrants|gay people|black people)[^.!?]{0,40}\\b(should (die|be exterminated)|deserve to die)\\b"
            + "|\\b(your diagnosis is|stop taking your prescribed|you should take \\d+\\s*mg)\\b"
            + "|\\b(you should invest all|guaranteed investment returns|buy this stock now)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern BOILERPLATE = Pattern.compile(
            "\\b(i (cannot|can't|am unable to) (help|assist|provide|answer)|as an ai (language )?model|"
            + "internal server error|service unavailable|request timed out|something went wrong)\\b",
            Pattern.CASE_INSENSITIVE);

    public void validateRequest(SparkRequest request) {
        if (request == null) reject("missing_required_field");
        validateInput(request.getMood(), 100);
        validateInput(request.getTime(), 100);
        validateInput(request.getInterests(), 300);
        validateInput(request.getQuestion(), 300);
        if ("question".equals(request.getPurpose()) && isBlank(request.getQuestion())) reject("missing_required_field");
        validateHistory(request.getSeenTasks());
    }

    public void validateInput(String text, int maximum) {
        if (text != null) {
            if (text.length() > maximum) reject("invalid_length");
            screen(text);
        }
    }

    public void validateHistory(List<String> history) {
        if (history == null) return;
        if (history.size() > 30) reject("invalid_length");
        // Prior titles are comparison data, not instructions or newly generated content.
        for (String text : history) if (text != null && text.length() > 300) reject("invalid_length");
    }

    public void validateSpark(SparkTaskDto task) {
        if (task == null) reject("missing_required_field");
        required(task.getTitle(), 3, 200);
        category(task.getCategory());
        required(task.getDescription(), 40, 6000);
        optional(task.getHook(), 500);
        optional(task.getFunFact(), 800);
        optional(task.getBadge(), 100);
        optional(task.getIcon(), 32);
        optional(task.getColor(), 20);
        if (task.getSteps() != null) {
            if (task.getSteps().size() > 8) reject("invalid_length");
            for (String step : task.getSteps()) required(step, 3, 1000);
        }
        if (task.getLink() != null) {
            optional(task.getLink().getText(), 200);
            String url = task.getLink().getUrl();
            optional(url, 500);
            if (url != null && !url.isBlank() && !url.startsWith("https://")) reject("content_rejected");
        }
    }

    public void validateMicroLearning(SparkTaskDto task, SparkRequest request) {
        String text = String.join(" ", Objects.toString(task.getHook(), ""), task.getDescription(),
                String.join(" ", task.getSteps()), Objects.toString(task.getFunFact(), ""));
        if (words(text) > 220 || words(task.getDescription()) < 15) reject("invalid_length");
        if (task.getDescription().split("\\R\\s*\\R").length > 2) reject("invalid_length");
        if ("interception".equals(request.getPurpose())) question(task.getTitle());
        rejectRecentDuplicate(task.getTitle(), request.getSeenTasks());
    }

    public void validateQuestions(List<QuestionCardDto> cards, List<String> recent) {
        if (cards == null || cards.size() != 6) reject("missing_required_field");
        validateHistory(recent);
        Set<String> seen = new HashSet<>();
        for (QuestionCardDto card : cards) {
            if (card == null) reject("missing_required_field");
            category(card.domain());
            question(card.question());
            optional(card.emoji(), 32);
            optional(card.tagColor(), 20);
            if (!seen.add(normalizeQuestion(card.question()))) reject("duplicate_question");
            rejectRecentDuplicate(card.question(), recent);
        }
    }

    public void validateChat(String answer) {
        required(answer, 20, 3000);
    }

    public void validateFlashcards(List<Map<String, String>> cards) {
        if (cards == null || cards.isEmpty() || cards.size() > 6) reject("missing_required_field");
        for (Map<String, String> card : cards) {
            if (card == null) reject("missing_required_field");
            required(card.get("question"), 8, 300);
            required(card.get("answer"), 10, 1500);
        }
    }

    public String normalizeQuestion(String text) {
        return Normalizer.normalize(Objects.toString(text, ""), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    public void validateReview(List<com.spark.dto.ReviewDtos.GeneratedQuestion> questions, Set<String> sourceIds) {
        if (questions == null || questions.size() != 5 || sourceIds.size() != 5) reject("missing_required_field");
        Set<String> usedSources = new HashSet<>();
        Set<String> usedQuestions = new HashSet<>();
        for (var item : questions) {
            if (item == null || !sourceIds.contains(item.sourceEntryId())
                    || !usedSources.add(item.sourceEntryId())) reject("invalid_source");
            required(item.question(), 12, 300);
            required(item.explanation(), 20, 1000);
            if (!usedQuestions.add(normalizeQuestion(item.question()))) reject("duplicate_question");
            if (item.options() == null || item.options().size() != 4 || item.correctOptionIndex() == null
                    || item.correctOptionIndex() < 0 || item.correctOptionIndex() > 3) reject("invalid_options");
            Set<String> options = new HashSet<>();
            for (String option : item.options()) {
                required(option, 1, 300);
                String key = normalizeQuestion(option);
                if (key.isBlank() || !options.add(key)) reject("invalid_options");
            }
        }
    }

    private void rejectRecentDuplicate(String text, List<String> recent) {
        String normalized = normalizeQuestion(text);
        if (recent != null && recent.stream().filter(Objects::nonNull)
                .anyMatch(previous -> normalized.equals(normalizeQuestion(previous)))) reject("duplicate_question");
    }

    private void category(String category) {
        required(category, 2, 40);
        if (!ALLOWED_CATEGORIES.contains(category.trim().toLowerCase(Locale.ROOT))) reject("invalid_category");
    }

    private void question(String text) {
        required(text, 12, 200);
        if (words(text) < 4 || words(text) > 30) reject("invalid_length");
    }

    private int words(String text) { return text.trim().split("\\s+").length; }
    private boolean isBlank(String text) { return text == null || text.isBlank(); }
    private void required(String text, int minimum, int maximum) {
        if (isBlank(text)) reject("missing_required_field");
        if (text.trim().length() < minimum || text.length() > maximum) reject("invalid_length");
        screen(text);
    }
    private void optional(String text, int maximum) {
        if (text != null) {
            if (text.length() > maximum) reject("invalid_length");
            screen(text);
        }
    }
    private void screen(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replace('\u2019', '\'').replaceAll("\\s+", " ");
        if (UNSAFE.matcher(normalized).find() || BOILERPLATE.matcher(normalized).find()) reject("content_rejected");
    }
    private void reject(String reason) {
        log.warn("AI validation failed: {}", reason);
        throw new IllegalStateException(reason);
    }
}
