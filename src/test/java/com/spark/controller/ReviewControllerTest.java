package com.spark.controller;

import com.spark.config.SecurityConfig;
import com.spark.dto.ReviewDtos.*;
import com.spark.repository.UserRepository;
import com.spark.security.JwtAuthFilter;
import com.spark.security.JwtUtil;
import com.spark.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ReviewController.class, properties = {
        "app.jwt.secret=knowledge-check-test-only-secret-at-least-32-bytes", "app.jwt.expiration-ms=60000",
        "google.client-id=test-client"})
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtUtil.class})
class ReviewControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @MockBean ReviewService service;
    @MockBean UserRepository users;
    private final UUID id = UUID.randomUUID();
    private String token() { return "Bearer " + jwt.generateToken("owner@example.com"); }

    @Test void allReviewRoutesRequireAuthentication() throws Exception {
        mvc.perform(post("/api/v1/reviews/start")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/reviews/history")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/reviews/" + id + "/submit").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void startUsesJwtOwnerAndNeverExposesCorrectAnswerOrExplanation() throws Exception {
        when(service.start("owner@example.com")).thenReturn(new Started(id,
                List.of(new Question("source", "Topic", "Why does this happen?", List.of("one", "two", "three", "four")))));
        mvc.perform(post("/api/v1/reviews/start").param("email", "other@example.com").header("Authorization", token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.questions[0].options.length()").value(4))
                .andExpect(jsonPath("$.questions[0].correctOptionIndex").doesNotExist())
                .andExpect(jsonPath("$.questions[0].explanation").doesNotExist());
        verify(service).start("owner@example.com");
    }
    @Test void submitIgnoresClientScoreAndUsesAuthenticatedOwner() throws Exception {
        when(service.submit(eq("owner@example.com"), eq(id), any())).thenReturn(new Result(id, Instant.now(), 4, 5, List.of()));
        mvc.perform(post("/api/v1/reviews/" + id + "/submit").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[0,1,2,3,0],\"score\":5,\"email\":\"other@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.correctCount").value(4));
        verify(service).submit("owner@example.com", id, new Submission(List.of(0, 1, 2, 3, 0)));
    }
    @Test void historyUsesJwtOwnerAndDoesNotReturnAbandonedAttempt() throws Exception {
        when(service.history("owner@example.com")).thenReturn(new History(3, List.of()));
        mvc.perform(get("/api/v1/reviews/history").header("Authorization", token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligibleCount").value(3))
                .andExpect(jsonPath("$.results").isEmpty());
        verify(service).history("owner@example.com");
    }
    @Test void insufficientHistoryAndAiFailureReturnControlledStatus() throws Exception {
        when(service.start("owner@example.com")).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Explore five ideas."));
        mvc.perform(post("/api/v1/reviews/start").header("Authorization", token())).andExpect(status().isConflict());
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Try again.")).when(service).start("owner@example.com");
        mvc.perform(post("/api/v1/reviews/start").header("Authorization", token())).andExpect(status().isServiceUnavailable());
    }
}
