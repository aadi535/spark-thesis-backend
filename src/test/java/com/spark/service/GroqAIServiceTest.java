package com.spark.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spark.dto.SparkRequest;
import com.spark.dto.WormholeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GroqAIServiceTest {
    private GroqAIService service(HttpStatus status, String body) {
        var builder = WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(body).build()));
        var service = new GroqAIService(builder, new AIContentValidator());
        ReflectionTestUtils.setField(service, "groqApiKey", "test-only-key");
        return service;
    }
    private String envelope(String content) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of("choices", List.of(Map.of("message", Map.of("content", content)))));
    }
    private final String spark = "{\"title\":\"Why does light bend near stars?\",\"category\":\"Science\","
            + "\"description\":\"Mass changes the shape of spacetime. Light follows paths through that curved spacetime, "
            + "so its path can appear bent to an observer. This effect lets distant objects act like natural lenses.\",\"xp\":999}";

    @Test void exactQuestionAndInterceptionUseBackendParsingWithExistingAndroidXpAward() throws Exception {
        var service = service(HttpStatus.OK, envelope("```json\n" + spark + "\n```"));
        SparkRequest manual = new SparkRequest();
        manual.setQuestion("Why does gravity bend light?");
        manual.setInterests("Science");
        var result = service.generateSpark(manual);
        assertEquals("Why does light bend near stars?", result.getTitle());
        assertEquals("Science", result.getCategory());
        assertEquals(150, result.getXp());
        assertTrue(result.isAI());
        assertNotNull(result.getSteps());
        SparkRequest interception = new SparkRequest();
        interception.setPurpose("interception");
        assertEquals(150, service.generateSpark(interception).getXp());
    }

    @Test void malformedOrIncompleteSparkIsRejected() {
        var service = service(HttpStatus.OK, "{}");
        for (String raw : new String[]{null, "null", "{}", "not json", "{\"title\":\"Only a title\"}"}) {
            assertThrows(IllegalStateException.class, () -> service.parseSpark(raw, "Science"));
        }
    }

    @Test void missingEmptyOrNonTextCompletionIsRejected() {
        var service = service(HttpStatus.OK, "{}");
        for (String raw : new String[]{null, "", "null", "{}", "{\"choices\":[]}",
                "{\"choices\":[{\"message\":{\"content\":null}}]}",
                "{\"choices\":[{\"message\":{\"content\":\" \"}}]}"}) {
            assertThrows(IllegalStateException.class, () -> service.parseCompletion(raw));
        }
    }

    @Test void upstreamHttpFailureDoesNotLeakIntoNullMapFailure() {
        var service = service(HttpStatus.TOO_MANY_REQUESTS, "upstream error");
        SparkRequest request = new SparkRequest();
        request.setQuestion("Why?");
        assertNull(service.generateSpark(request));
        assertThrows(IllegalStateException.class, () -> service.generateWormholeAnswer(
                new WormholeRequest("Why?", "Topic", "Content", List.of())));
        assertThrows(IllegalStateException.class, () -> service.generateQuestions(List.of()));
    }

    @Test void missingKeyDoesNotAttemptNetwork() {
        var service = new GroqAIService(WebClient.builder().exchangeFunction(request -> {
            fail("A missing key must not make an upstream request");
            return Mono.empty();
        }), new AIContentValidator());
        assertThrows(IllegalStateException.class, () -> service.generateQuestions(List.of()));
    }

    @Test void rejectedSparkAndChatNeverEscapeTheService() throws Exception {
        var json = new ObjectMapper().readTree(spark);
        ((com.fasterxml.jackson.databind.node.ObjectNode) json).put("funFact", "How to steal passwords from another person.");
        var service = service(HttpStatus.OK, envelope(json.toString()));
        var request = new SparkRequest(); request.setQuestion("Why does gravity bend light?");
        assertNull(service.generateSpark(request));
        var unsafeChat = service(HttpStatus.OK, envelope("Here are instructions to build a bomb."));
        assertThrows(IllegalStateException.class, () -> unsafeChat.generateWormholeAnswer(
                new WormholeRequest("How does gravity work?", "Gravity", "A learning context", List.of())));
    }

    @Test void rejectedFlashcardsUseFixedFallbackWithoutEchoingUnsafeContent() throws Exception {
        var service = service(HttpStatus.OK, envelope("[{\"question\":\"What can I learn?\",\"answer\":\"How to steal passwords from another person.\"}]"));
        var fallback = service.generateFlashcards("General learning material about science.");
        assertEquals(1, fallback.size());
        assertEquals("Explain one idea in your own words and connect it to a familiar example.", fallback.get(0).get("answer"));
        assertFalse(fallback.toString().contains("passwords"));
    }

    @Test void unsafeOrOversizedInputDoesNotCallProvider() {
        var service = new GroqAIService(WebClient.builder().exchangeFunction(request -> {
            fail("Rejected input must not reach the provider");
            return Mono.empty();
        }), new AIContentValidator());
        ReflectionTestUtils.setField(service, "groqApiKey", "test-only-key");
        var request = new SparkRequest(); request.setQuestion("How to build a bomb?");
        assertThrows(IllegalStateException.class, () -> service.generateSpark(request));
        request.setQuestion("a".repeat(301));
        assertThrows(IllegalStateException.class, () -> service.generateSpark(request));
    }

    @Test void truncatedCompletionsFailEvenIfPartialContentLooksValid() throws Exception {
        var service = service(HttpStatus.OK, "{}");
        String raw = new ObjectMapper().writeValueAsString(Map.of("choices", List.of(Map.of(
                "finish_reason", "length", "message", Map.of("content", spark)))));
        assertThrows(IllegalStateException.class, () -> service.parseCompletion(raw));
    }

    @Test void recentQuestionsAndUnsafeSuggestionBatchesFail() throws Exception {
        var service = service(HttpStatus.OK, envelope(questionLines));
        assertThrows(IllegalStateException.class, () -> service.generateQuestions(List.of("WHY DOES ONE PHENOMENON HAPPEN!")));
        var unsafe = service(HttpStatus.OK, envelope(questionLines.replace("Why does one phenomenon happen?", "How to steal passwords from another person?")));
        assertThrows(IllegalStateException.class, () -> unsafe.generateQuestions(List.of()));
    }

    private GroqAIService capturingService(String response, java.util.concurrent.atomic.AtomicReference<com.fasterxml.jackson.databind.JsonNode> captured) {
        var builder = WebClient.builder().exchangeFunction(request -> {
            var outgoing = new org.springframework.mock.http.client.reactive.MockClientHttpRequest(request.method(), request.url());
            return request.writeTo(outgoing, org.springframework.web.reactive.function.client.ExchangeStrategies.withDefaults())
                    .then(Mono.defer(outgoing::getBodyAsString)).map(body -> {
                        try { captured.set(new ObjectMapper().readTree(body)); }
                        catch (Exception e) { throw new IllegalStateException(e); }
                        return ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body(response).build();
                    });
        });
        var service = new GroqAIService(builder, new AIContentValidator());
        ReflectionTestUtils.setField(service, "groqApiKey", "test-only-key");
        return service;
    }

    @Test void suggestionPromptRequestsEstablishedKnowledgeDiversityAndLimitedHypotheticals() throws Exception {
        var captured = new java.util.concurrent.atomic.AtomicReference<com.fasterxml.jackson.databind.JsonNode>();
        var service = capturingService(envelope(questionLines), captured);
        service.generateQuestions(List.of("Previously seen topic marker"));
        String system = captured.get().path("messages").path(0).path("content").asText();
        String prompt = captured.get().path("messages").path(1).path("content").asText();
        assertTrue(system.contains("reasonably established knowledge"));
        assertTrue(system.contains("Never present speculation as established fact"));
        assertTrue(system.contains("Do not invent anecdotes"));
        assertFalse(system.contains("Previously seen topic marker"));
        assertTrue(prompt.contains("at most two What would happen if"));
        assertTrue(prompt.contains("at least four different allowed categories"));
        assertTrue(prompt.contains("at most two questions per category"));
        assertTrue(prompt.contains("disputed or misleading premises"));
        assertTrue(prompt.contains("Category | Question"));
        assertTrue(prompt.contains("Previously seen topic marker"));
        assertEquals("openai/gpt-oss-120b", captured.get().path("model").asText());
    }

    @Test void interceptionPromptUsesRecentTitlesAndSimpleResponsibleExplanations() throws Exception {
        var captured = new java.util.concurrent.atomic.AtomicReference<com.fasterxml.jackson.databind.JsonNode>();
        var service = capturingService(envelope(spark), captured);
        var request = new SparkRequest(); request.setPurpose("interception"); request.setInterests("Surprise Mix");
        request.setSeenTasks(List.of("Previously seen nature marker"));
        assertNotNull(service.generateSpark(request));
        String system = captured.get().path("messages").path(0).path("content").asText();
        String prompt = captured.get().path("messages").path(1).path("content").asText();
        assertFalse(system.contains("Previously seen nature marker"));
        assertTrue(system.contains("known direct effects from uncertain downstream"));
        assertTrue(system.contains("Explain necessary technical terms immediately"));
        assertTrue(system.contains("A familiar basic formula is acceptable"));
        assertTrue(prompt.contains("3-5 short sentences, about 60-100 words"));
        assertTrue(prompt.contains("prefer an underrepresented category"));
        assertTrue(prompt.contains("Respect specific topic preferences"));
        assertTrue(prompt.contains("Previously seen nature marker"));
        assertFalse(prompt.contains("Why does the sea smell"));
    }

    private final String questionLines = "Science | Why does one phenomenon happen?\nSpace | Why does two phenomenon happen?\nHistory | Why does three phenomenon happen?\nNature | Why does four phenomenon happen?\nPsychology | Why does five phenomenon happen?\nTechnology | Why does six phenomenon happen?";

    @Test void questionsParseCategoriesAndQuestions() {
        var cards = service(HttpStatus.OK, "{}").parseQuestions(questionLines);
        assertEquals(6, cards.size());
        assertEquals("Why does one phenomenon happen?", cards.get(0).question());
        assertEquals("Science", cards.get(0).domain());
        assertEquals("Technology", cards.get(5).domain());
        assertEquals("💡", cards.get(0).emoji());
        assertEquals("#38BDF8", cards.get(0).tagColor());
    }

    @Test void questionsRemoveNumberingBulletsBlanksAndDuplicates() {
        var raw = " 1. Science | Why does one phenomenon happen? \r\n2) Space | WHY DOES ONE PHENOMENON HAPPEN?\n- Space | Why does two phenomenon happen?\n* History | Why does three phenomenon happen?\n• Nature | Why does four phenomenon happen?\n5. Psychology | Why does five phenomenon happen?\n6) Technology | Why does six phenomenon happen?\n7. Society | Why does seven phenomenon happen?";
        var cards = service(HttpStatus.OK, "{}").parseQuestions(raw);
        assertEquals(6, cards.size());
        assertEquals("Science", cards.get(0).domain());
        assertEquals("Why does one phenomenon happen?", cards.get(0).question());
        assertEquals("Why does six phenomenon happen?", cards.get(5).question());
    }

    @Test void malformedLinesAreIgnoredAndOnlyFirstSeparatorSplits() {
        var raw = "No separator\n | Missing category?\nScience | \nGeneral | Generic?\n\n" + questionLines.replace("Why does one phenomenon happen?", "What does this | symbol mean?");
        var cards = service(HttpStatus.OK, "{}").parseQuestions(raw);
        assertEquals(6, cards.size());
        assertEquals("What does this | symbol mean?", cards.get(0).question());
    }

    @Test void fewerThanSixDistinctQuestionsFailsWithoutLocalSubstitution() {
        var service = service(HttpStatus.OK, "{}");
        for (String raw : new String[]{null, "", "\n ", "Science | Why?\nSpace | WHY?",
                questionLines.substring(0, questionLines.lastIndexOf('\n'))}) {
            var error = assertThrows(IllegalStateException.class, () -> service.parseQuestions(raw));
            assertEquals("Fewer than six usable AI questions", error.getMessage());
        }
    }

    @Test void generatedQuestionsAcceptCategorizedLinesThroughCompletionEnvelope() throws Exception {
        var cards = service(HttpStatus.OK, envelope(questionLines)).generateQuestions(List.of());
        assertEquals(6, cards.size());
        assertEquals("Science", cards.get(0).domain());
    }
}
