package com.spark.security;

import com.spark.config.SecurityConfig;
import com.spark.controller.AuthController;
import com.spark.controller.UserController;
import com.spark.controller.SparkController;
import com.spark.repository.UserRepository;
import com.spark.service.AuthService;
import com.spark.service.UserService;
import com.spark.service.GroqAIService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, UserController.class, SparkController.class},
        properties = {"app.jwt.secret=authentication-test-only-secret-with-at-least-32-bytes",
                "app.jwt.expiration-ms=60000", "google.client-id=test-client"})
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtUtil.class})
class AuthenticationAccessTest {
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @MockBean UserRepository users;
    @MockBean AuthService authService;
    @MockBean UserService userService;
    @MockBean GroqAIService groq;

    @Test void userAndSparkRoutesRequireBearerJwt() throws Exception {
        mvc.perform(get("/api/v1/user/progress")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/user/progress").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/spark").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/spark/wormhole").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService, groq);
    }

    @Test void invalidAndExpiredJwtCannotAccessProtectedRoute() throws Exception {
        mvc.perform(get("/api/v1/user/leaderboard").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        ReflectionTestUtils.setField(jwt, "jwtExpirationMs", -10000L);
        String expired;
        try { expired = jwt.generateToken("student@example.com"); }
        finally { ReflectionTestUtils.setField(jwt, "jwtExpirationMs", 60000L); }
        mvc.perform(get("/api/v1/user/leaderboard").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test void validBackendJwtReachesProtectedController() throws Exception {
        when(userService.getLeaderboard("student@example.com")).thenReturn(
                new com.spark.dto.LeaderboardResponse(List.of(), null));
        mvc.perform(get("/api/v1/user/leaderboard")
                .header("Authorization", "Bearer " + jwt.generateToken("student@example.com")))
                .andExpect(status().isOk());
        verify(userService).getLeaderboard("student@example.com");
    }

    @Test void googleExchangeRemainsPublic() throws Exception {
        mvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"google-token\"}")).andExpect(status().isOk());
        verify(authService).authenticateWithGoogle("google-token");
    }

    @Test void removedTestEndpointCannotIssueToken() throws Exception {
        mvc.perform(get("/api/v1/auth/test-token")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/test-token")
                .header("Authorization", "Bearer " + jwt.generateToken("student@example.com")))
                .andExpect(status().isNotFound());
        verifyNoInteractions(authService);
    }
}
