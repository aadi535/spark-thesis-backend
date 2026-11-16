package com.spark.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.spark.dto.AuthResponse;
import com.spark.model.User;
import com.spark.repository.UserRepository;
import com.spark.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.security.GeneralSecurityException;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    private final GoogleIdTokenVerifier googleVerifier;

    public AuthService(UserRepository userRepository, JwtUtil jwtUtil, GoogleIdTokenVerifier googleVerifier) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.googleVerifier = googleVerifier;
    }

    @Transactional
    public AuthResponse authenticateWithGoogle(String idTokenString) {
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google ID token");
        }
        final GoogleIdToken idToken;
        try {
            idToken = googleVerifier.verify(idTokenString);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google verification unavailable");
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google ID token");
        }
        if (idToken == null || !Boolean.TRUE.equals(idToken.getPayload().getEmailVerified()) ||
                idToken.getPayload().getEmail() == null || idToken.getPayload().getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A verified Google email is required");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String image = (String) payload.get("picture");

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            log.info("New user signing up: {}", email);
            User newUser = User.builder()
                    .email(email)
                    .name(name)
                    .image(image)
                    .build();
            return userRepository.save(newUser);
        });

        if (name != null && (user.getName() == null || user.getName().isBlank())) user.setName(name);
        if (image != null && !image.equals(user.getImage())) user.setImage(image);
        userRepository.save(user);

        String token = jwtUtil.generateToken(email);

        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setEmail(email);
        response.setName(user.getName());
        response.setImage(user.getImage());
        response.setPro(user.isPro());
        return response;

    }
}
