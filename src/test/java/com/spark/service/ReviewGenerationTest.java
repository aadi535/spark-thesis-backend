package com.spark.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spark.dto.ReviewDtos.GeneratedQuestion;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class ReviewGenerationTest {
    private final AIContentValidator validator = new AIContentValidator();
    private final ObjectMapper json = new ObjectMapper();
    private final Set<String> ids = Set.of("source-0", "source-1", "source-2", "source-3", "source-4");
    private List<GeneratedQuestion> valid() {
        return IntStream.range(0, 5).mapToObj(i -> ReviewServiceTest.question("source-" + i, i)).toList();
    }
    private void rejects(GeneratedQuestion first) {
        var questions = new ArrayList<>(valid());
        questions.set(0, first);
        assertThrows(IllegalStateException.class, () -> validator.validateReview(questions, ids));
    }
    @Test void validGroundedBatchPasses() { assertDoesNotThrow(() -> validator.validateReview(valid(), ids)); }
    @Test void missingOrWrongNumberFails() {
        assertThrows(IllegalStateException.class, () -> validator.validateReview(null, ids));
        assertThrows(IllegalStateException.class, () -> validator.validateReview(valid().subList(0, 4), ids));
        rejects(null);
    }
    @Test void blankAndLongFieldsFail() {
        var q = valid().getFirst();
        for (String question : List.of(" ", "short", "x".repeat(301)))
            rejects(new GeneratedQuestion(q.sourceEntryId(), question, q.options(), 0, q.explanation()));
        rejects(new GeneratedQuestion(q.sourceEntryId(), q.question(), q.options(), 0, " "));
        rejects(new GeneratedQuestion(q.sourceEntryId(), q.question(), q.options(), 0, "x".repeat(1001)));
    }
    @Test void unknownRepeatedSourcesAndRepeatedQuestionsFail() {
        var q = valid().getFirst();
        rejects(new GeneratedQuestion("foreign", q.question(), q.options(), 0, q.explanation()));
        rejects(new GeneratedQuestion("source-1", q.question(), q.options(), 0, q.explanation()));
        rejects(new GeneratedQuestion(q.sourceEntryId(), valid().get(1).question().toUpperCase(), q.options(), 0, q.explanation()));
    }
    @Test void optionsAndAnswerKeysMustBeValidAndDistinct() {
        var q = valid().getFirst();
        for (Integer key : Arrays.asList(null, -1, 4))
            rejects(new GeneratedQuestion(q.sourceEntryId(), q.question(), q.options(), key, q.explanation()));
        for (var options : List.of(List.of("one"), List.of("same", "SAME!", "third", "fourth"), List.of(" ", "b", "c", "d")))
            rejects(new GeneratedQuestion(q.sourceEntryId(), q.question(), options, 0, q.explanation()));
    }
    @Test void safetyScreenCoversOptionsAndExplanations() {
        var q = valid().getFirst();
        rejects(new GeneratedQuestion(q.sourceEntryId(), q.question(), List.of("pornographic", "b", "c", "d"), 0, q.explanation()));
        rejects(new GeneratedQuestion(q.sourceEntryId(), q.question(), q.options(), 0, "As an AI language model I cannot help with this."));
    }
    @Test void actualGenerationHelperMakesOneProviderCallAndUsesExistingModel() throws Exception {
        var count = new AtomicInteger();
        String envelope = json.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", "stop",
                "message", Map.of("content", json.writeValueAsString(valid()))))));
        var service = new GroqAIService(WebClient.builder().exchangeFunction(request -> {
            count.incrementAndGet();
            assertEquals("https://api.groq.com/openai/v1/chat/completions", request.url().toString());
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body(envelope).build());
        }), validator);
        ReflectionTestUtils.setField(service, "groqApiKey", "test-only-key");
        assertEquals(5, service.generateReview(ReviewServiceTest.entries(5)).size());
        assertEquals(1, count.get());
    }
    @Test void malformedProviderQuizIsRejectedWithoutFallback() {
        var service = new GroqAIService(WebClient.builder(), validator);
        for (String raw : Arrays.asList(null, "", "not JSON", "{}", "[]", "[{}]"))
            assertThrows(IllegalStateException.class, () -> service.parseReview(raw, ids));
    }
}
