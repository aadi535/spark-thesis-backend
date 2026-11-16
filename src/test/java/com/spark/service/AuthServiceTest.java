package com.spark.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.spark.model.User;
import com.spark.repository.UserRepository;
import com.spark.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final JwtUtil jwt = mock(JwtUtil.class);
    private final GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
    private final AuthService service = new AuthService(users, jwt, verifier);

    private GoogleIdToken token(boolean verified) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("student@example.com");
        payload.setEmailVerified(verified);
        payload.set("name", "Student");
        return new GoogleIdToken(new JsonWebSignature.Header(), payload, new byte[0], new byte[0]);
    }

    @Test void verifiedGoogleIdentityReceivesBackendJwt() throws Exception {
        when(verifier.verify("google-token")).thenReturn(token(true));
        when(users.findByEmail("student@example.com")).thenReturn(Optional.of(
                User.builder().email("student@example.com").name("Student").build()));
        when(jwt.generateToken("student@example.com")).thenReturn("backend-jwt");
        var response = service.authenticateWithGoogle("google-token");
        assertEquals("backend-jwt", response.getToken());
        assertEquals("student@example.com", response.getEmail());
        verify(verifier).verify("google-token");
    }

    @Test void googleLoginPreservesChosenDisplayName() throws Exception {
        when(verifier.verify("google-token")).thenReturn(token(true));
        var user = User.builder().email("student@example.com").name("Chosen Name").build();
        when(users.findByEmail("student@example.com")).thenReturn(Optional.of(user));
        assertEquals("Chosen Name", service.authenticateWithGoogle("google-token").getName());
        assertEquals("Chosen Name", user.getName());
    }

    @Test void invalidTokenAndFormerBypassCannotIssueJwt() throws Exception {
        for (String value : new String[]{"test-bypass", "invalid"}) {
            var error = assertThrows(ResponseStatusException.class, () -> service.authenticateWithGoogle(value));
            assertEquals(401, error.getStatusCode().value());
            verify(verifier).verify(value);
        }
        verifyNoInteractions(users, jwt);
    }

    @Test void blankTokenRejectedBeforeVerification() {
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.authenticateWithGoogle(" ")).getStatusCode().value());
        verifyNoInteractions(verifier, users, jwt);
    }

    @Test void unverifiedEmailCannotReceiveJwt() throws Exception {
        when(verifier.verify("unverified")).thenReturn(token(false));
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.authenticateWithGoogle("unverified")).getStatusCode().value());
        verifyNoInteractions(users, jwt);
    }

    @Test void malformedTokenReturnsUnauthorized() throws Exception {
        when(verifier.verify("malformed")).thenThrow(new IllegalArgumentException());
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.authenticateWithGoogle("malformed")).getStatusCode().value());
    }

    @Test void verifierOutageDoesNotCreateSession() throws Exception {
        when(verifier.verify("token")).thenThrow(new IOException());
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> service.authenticateWithGoogle("token")).getStatusCode().value());
        verifyNoInteractions(users, jwt);
    }

    @Test void googleLoginCreatesCleanUserWhenPreviousAccountWasDeleted() throws Exception {
        when(verifier.verify("google-token")).thenReturn(token(true));
        when(users.findByEmail("student@example.com")).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.authenticateWithGoogle("google-token");
        var saved = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users, atLeastOnce()).save(saved.capture());
        for (User user : saved.getAllValues()) {
            assertEquals(0, user.getTotalXp());
            assertEquals(0, user.getStreak());
            assertNull(user.getCompletedTasks());
            assertNull(user.getSeenTasks());
            assertTrue(user.getFlashcards() == null || user.getFlashcards().isEmpty());
        }
    }
}
