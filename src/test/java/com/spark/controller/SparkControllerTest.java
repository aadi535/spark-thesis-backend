package com.spark.controller;

import com.spark.config.SecurityConfig;
import com.spark.dto.*;
import com.spark.repository.UserRepository;
import com.spark.security.JwtAuthFilter;
import com.spark.security.JwtUtil;
import com.spark.service.GroqAIService;
import com.spark.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SparkController.class, properties = {
    "app.jwt.secret=ai-endpoint-test-only-secret-at-least-32-bytes", "app.jwt.expiration-ms=60000"})
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtUtil.class})
class SparkControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @MockBean GroqAIService groq;
    @MockBean UserService users;
    @MockBean UserRepository repository;
    UserProgressDto progress;
    String authorization;

    @BeforeEach void setup() {
        authorization = "Bearer " + jwt.generateToken("learner@example.com");
        progress = new UserProgressDto();
        progress.setXp(900);
        progress.setStreak(7);
        progress.setDailyGeneratedDate(LocalDate.now().toString());
        when(users.getProgress("learner@example.com")).thenReturn(progress);
    }

    private SparkTaskDto task() {
        return SparkTaskDto.builder().title("Gravity explained").description("A concrete mechanism")
                .xp(150).isAI(true).build();
    }

    @Test void manualQuestionReturnsTaskAndWritesOnlyUsageFields() throws Exception {
        when(groq.generateSpark(any())).thenReturn(task());
        mvc.perform(post("/api/v1/spark").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Why does gravity bend light?\",\"interests\":\"Science\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.task.title").value("Gravity explained"))
                .andExpect(jsonPath("$.task.xp").value(150)).andExpect(jsonPath("$.task.ai").value(true));
        verify(groq).generateSpark(argThat(r -> "manual".equals(r.getPurpose())
                && "Why does gravity bend light?".equals(r.getQuestion())));
        verify(users).saveProgress(eq("learner@example.com"), argThat(p -> p.getDailyGeneratedCount() == 1
                && p.getXp() == 0 && p.getStreak() == -1 && p.getCompletedTasks() == null));
    }

    @Test void exhaustedManualQuotaDoesNotCallGroq() throws Exception {
        progress.setDailyGeneratedCount(3);
        mvc.perform(post("/api/v1/spark").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.limitReached").value(true));
        verifyNoInteractions(groq);
        verify(users, never()).saveProgress(anyString(), any());
    }

    @Test void interceptionAndPrefetchDoNotConsumeManualAllowance() throws Exception {
        progress.setDailyGeneratedCount(3);
        when(groq.generateSpark(any())).thenReturn(task());
        mvc.perform(post("/api/v1/spark").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{\"purpose\":\"interception\"}"))
                .andExpect(status().isOk());
        verify(users, never()).saveProgress(anyString(), any());
    }

    @Test void selectedQuestionBypassesExhaustedManualQuotaWithoutConsumingIt() throws Exception {
        progress.setDailyGeneratedCount(3);
        when(groq.generateSpark(any())).thenReturn(task());
        mvc.perform(post("/api/v1/spark").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"purpose":"question","question":"Why does gravity bend light?"}
                    """))
                .andExpect(status().isOk());
        verify(groq).generateSpark(argThat(r -> "question".equals(r.getPurpose())
                && "Why does gravity bend light?".equals(r.getQuestion())));
        verify(users, never()).saveProgress(anyString(), any());
    }

    @Test void selectedQuestionRequiresTextAndReturnsControlledFailure() throws Exception {
        mvc.perform(post("/api/v1/spark").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"purpose":"question","question":" "}
                    """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(groq);
        progress.setDailyGeneratedCount(3);
        mvc.perform(post("/api/v1/spark").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"purpose":"question","question":"Why?"}
                    """))
                .andExpect(status().isServiceUnavailable());
        verify(users, never()).saveProgress(anyString(), any());
    }

    @Test void unknownPurposeIsRejected() throws Exception {
        mvc.perform(post("/api/v1/spark").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{\"purpose\":\"unlimited\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(groq);
    }

    @Test void failedNullOrIncompleteSparkReturns503WithoutChargingQuota() throws Exception {
        when(groq.generateSpark(any())).thenReturn(null).thenReturn(new SparkTaskDto())
                .thenThrow(new IllegalStateException("upstream unavailable"));
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/spark").header("Authorization", authorization)
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").isString());
        }
        verify(users, never()).saveProgress(anyString(), any());
    }

    @Test void wormholePassesHistoryAndDoesNotConsumeManualQuota() throws Exception {
        when(groq.generateWormholeAnswer(any())).thenReturn("A direct answer");
        mvc.perform(post("/api/v1/spark/wormhole").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"question":"Why?","sparkTitle":"Gravity","sparkDescription":"Context",
                     "history":[{"role":"assistant","content":"Earlier explanation"}]}
                    """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("A direct answer"));
        verify(groq).generateWormholeAnswer(argThat(r -> r.history().size() == 1
                && "Earlier explanation".equals(r.history().get(0).content())));
        verify(users).saveProgress(anyString(), argThat(p -> p.getDailyWormholeCount() == 1
                && p.getDailyGeneratedDate() == null && p.getXp() == 0 && p.getStreak() == -1));
    }

    @Test void wormholeFailuresAreControlledAndEmptyQuestionIsRejected() throws Exception {
        mvc.perform(post("/api/v1/spark/wormhole").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        when(groq.generateWormholeAnswer(any())).thenReturn(null).thenReturn(" ")
                .thenThrow(new IllegalStateException());
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/spark/wormhole").header("Authorization", authorization)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Why?\"}"))
                    .andExpect(status().isServiceUnavailable());
        }
        verify(users, never()).saveProgress(anyString(), any());
    }

    @Test void questionRefreshReturnsCardsWithoutChargingManualQuota() throws Exception {
        var cards = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> new QuestionCardDto("Science", "?", "Question " + i, "#38BDF8")).toList();
        when(groq.generateQuestions(any())).thenReturn(cards);
        mvc.perform(post("/api/v1/spark/questions").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{\"seenQuestions\":[\"Old question\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions.length()").value(6));
        verify(groq).generateQuestions(List.of("Old question"));
        verify(users, never()).saveProgress(anyString(), any());
    }

    @Test void questionRefreshFailuresReturn503() throws Exception {
        when(groq.generateQuestions(any())).thenReturn(null).thenReturn(List.of())
                .thenThrow(new IllegalStateException());
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/spark/questions").header("Authorization", authorization)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"seenQuestions\":[]}"))
                    .andExpect(status().isServiceUnavailable());
        }
    }

    @Test void realServiceValidationPreventsRejectedContentReachingClientOrChargingQuota() throws Exception {
        String rejected = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("choices", List.of(Map.of(
                "message", Map.of("content", "{\"title\":\"Why does light bend near stars?\",\"category\":\"Science\","
                        + "\"description\":\"This is an explanation of a natural phenomenon for a general audience.\","
                        + "\"funFact\":\"How to steal passwords from another person.\"}")))));
        var builder = org.springframework.web.reactive.function.client.WebClient.builder().exchangeFunction(request ->
                reactor.core.publisher.Mono.just(org.springframework.web.reactive.function.client.ClientResponse.create(org.springframework.http.HttpStatus.OK)
                        .header("Content-Type", "application/json").body(rejected).build()));
        var realService = new GroqAIService(builder, new com.spark.service.AIContentValidator());
        org.springframework.test.util.ReflectionTestUtils.setField(realService, "groqApiKey", "test-only-key");
        when(groq.generateSpark(any())).thenAnswer(call -> realService.generateSpark(call.getArgument(0)));
        for (String purpose : List.of("manual", "question", "interception")) {
            mvc.perform(post("/api/v1/spark").header("Authorization", authorization)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"purpose\":\"" + purpose + "\",\"question\":\"Why does light bend near stars?\"}"))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.task").doesNotExist());
        }
        verify(users, never()).saveProgress(anyString(), any());
    }

    @Test void allAiEndpointsRequireJwt() throws Exception {
        for (String path : List.of("/api/v1/spark", "/api/v1/spark/wormhole", "/api/v1/spark/questions")) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(groq, users);
    }
}
