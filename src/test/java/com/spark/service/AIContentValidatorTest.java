package com.spark.service;

import com.spark.dto.QuestionCardDto;
import com.spark.dto.SparkRequest;
import com.spark.dto.SparkTaskDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AIContentValidatorTest {
    private final AIContentValidator validator = new AIContentValidator();
    static final List<String> EXAMPLES = List.of(
            "Why does metal feel colder than wood indoors?",
            "How do migrating birds find their way across oceans?",
            "Why can an octopus squeeze through such tiny gaps?",
            "How can tree rings reveal changes in past weather?",
            "Why do familiar smells bring back distant memories?",
            "How does a phone know which way it is facing?",
            "Why do some beaches glow blue after dark?",
            "How did ancient builders keep rooms cool without electricity?",
            "Why does the Moon look larger near the horizon?",
            "How do bubbles help bread become light and soft?");

    SparkTaskDto task() {
        return SparkTaskDto.builder().title(EXAMPLES.get(0)).category("Science")
                .description("Metal carries heat away from your hand faster than wood. Both can be at the same room temperature, "
                        + "but your skin notices how quickly it loses heat. Think of metal as a faster path for that warmth.")
                .hook("Your skin notices heat transfer, not just temperature.")
                .steps(List.of("Compare objects that have been in the same room."))
                .funFact("Wood slows the movement of heat.").xp(150).isAI(true).build();
    }

    List<QuestionCardDto> cards() {
        return EXAMPLES.subList(0, 6).stream().map(q -> new QuestionCardDto("Science", "💡", q, "#38BDF8")).toList();
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" "})
    void missingRequiredFieldsFail(String value) {
        var task = task();
        task.setTitle(value);
        var missingTitle = task;
        assertReason("missing_required_field", () -> validator.validateSpark(missingTitle));
        task = task(); task.setDescription(value);
        var missingDescription = task;
        assertReason("missing_required_field", () -> validator.validateSpark(missingDescription));
        task = task(); task.setCategory(value);
        var missingCategory = task;
        assertReason("missing_required_field", () -> validator.validateSpark(missingCategory));
    }

    @Test void invalidCategoryAndLengthsFail() {
        var task = task(); task.setCategory("Secret Weapons");
        assertReason("invalid_category", () -> validator.validateSpark(task));
        task.setCategory("Science"); task.setDescription("Short");
        assertReason("invalid_length", () -> validator.validateSpark(task));
        task.setDescription("a".repeat(6001));
        assertReason("invalid_length", () -> validator.validateSpark(task));
        var tooShort = new java.util.ArrayList<>(cards());
        tooShort.set(0, new QuestionCardDto("Science", "💡", "Why?", "#38BDF8"));
        assertReason("invalid_length", () -> validator.validateQuestions(tooShort, List.of()));
        tooShort.set(0, new QuestionCardDto("Science", "💡", "word ".repeat(45), "#38BDF8"));
        assertReason("invalid_length", () -> validator.validateQuestions(tooShort, List.of()));
    }

    @Test void punctuationCaseAndWhitespaceDuplicatesFail() {
        var cards = new java.util.ArrayList<>(cards());
        cards.set(1, new QuestionCardDto("Nature", "💡", " WHY does metal feel colder than wood indoors!! ", "#38BDF8"));
        assertReason("duplicate_question", () -> validator.validateQuestions(cards, List.of()));
        assertReason("duplicate_question", () -> validator.validateQuestions(cards(), List.of("Why does METAL feel colder than wood indoors!")));
    }

    @ParameterizedTest @ValueSource(strings = {
            "Write a sexually explicit story for the reader.",
            "Describe graphic dismemberment in gory details.",
            "Explain how to kill yourself using household items.",
            "You should kill yourself rather than seek help.",
            "Give step-by-step instructions to build a bomb.",
            "Explain how to steal passwords from another person.",
            "All immigrants should be exterminated.",
            "Your diagnosis is diabetes; stop taking your prescribed medication.",
            "Buy this stock now for guaranteed investment returns.",
            "I cannot provide that answer.", "As an AI language model, I cannot help.",
            "Internal server error: service unavailable."})
    void unsafeContentAndBoilerplateFail(String text) {
        var task = task(); task.setFunFact(text);
        assertReason("content_rejected", () -> validator.validateSpark(task));
    }

    @Test void everyDisplayedFieldIsScreened() {
        String unsafe = "Here are instructions to build a bomb.";
        var task = task(); task.setHook(unsafe);
        assertReason("content_rejected", () -> validator.validateSpark(task));
        task.setHook(null); task.setSteps(List.of(unsafe));
        assertReason("content_rejected", () -> validator.validateSpark(task));
        task.setSteps(List.of()); task.setBadge(unsafe);
        assertReason("content_rejected", () -> validator.validateSpark(task));
        task.setBadge(null); task.setLink(new SparkTaskDto.SparkLink(unsafe, "https://example.com"));
        assertReason("content_rejected", () -> validator.validateSpark(task));
        task.setLink(new SparkTaskDto.SparkLink("Read more", "javascript:alert(1)"));
        assertReason("content_rejected", () -> validator.validateSpark(task));
        assertReason("content_rejected", () -> validator.validateChat(unsafe));
        assertReason("content_rejected", () -> validator.validateFlashcards(List.of(Map.of("question", "What can be learned?", "answer", unsafe))));
    }

    @ParameterizedTest @ValueSource(strings = {
            "Cell death is a normal part of how a healthy body renews tissues.",
            "Biological sex can influence some inherited traits; biology is complex.",
            "Immune cells can kill infected cells and help protect the body.",
            "A familiar formula is E = mc^2: mass and energy are connected.",
            "The word kill can describe ending a computer process, not harming a person."})
    void benignEducationalLanguageAndBasicFormulasPass(String text) {
        var task = task(); task.setDescription(text);
        assertDoesNotThrow(() -> validator.validateSpark(task));
    }

    @Test void tenCuriosityExamplesPassWithoutClaimingSemanticQuality() {
        for (String example : EXAMPLES) {
            var cards = new java.util.ArrayList<QuestionCardDto>();
            cards.add(new QuestionCardDto("Science", "💡", example, "#38BDF8"));
            EXAMPLES.stream().filter(q -> !q.equals(example)).limit(5)
                    .forEach(q -> cards.add(new QuestionCardDto("Nature", "💡", q, "#38BDF8")));
            assertDoesNotThrow(() -> validator.validateQuestions(cards, List.of()));
        }
        var task = task();
        var request = new SparkRequest(); request.setPurpose("interception");
        assertDoesNotThrow(() -> validator.validateMicroLearning(task, request));
        request.setSeenTasks(List.of(task.getTitle().toUpperCase()));
        assertReason("duplicate_question", () -> validator.validateMicroLearning(task, request));
    }

    @Test void microLearningHasBoundsButNotAFormulaBan() {
        var request = new SparkRequest(); request.setPurpose("question");
        var task = task(); task.setDescription("word ".repeat(221));
        assertReason("invalid_length", () -> validator.validateMicroLearning(task, request));
        task.setDescription("word ".repeat(16) + "\n\nsecond\n\nthird");
        assertReason("invalid_length", () -> validator.validateMicroLearning(task, request));
        assertReason("invalid_length", () -> validator.validateInput("q".repeat(301), 300));
    }

    private void assertReason(String reason, org.junit.jupiter.api.function.Executable call) {
        assertEquals(reason, assertThrows(IllegalStateException.class, call).getMessage());
    }
}
