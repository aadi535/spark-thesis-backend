package com.spark.controller;

import com.spark.dto.*;
import com.spark.service.GroqAIService;
import com.spark.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/spark")
public class SparkController {
    private final GroqAIService groqAIService;
    private final UserService userService;

    public SparkController(GroqAIService groqAIService, UserService userService) {
        this.groqAIService = groqAIService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<?> generateSpark(@RequestBody SparkRequest request, Authentication auth) {
        String purpose = request.getPurpose() == null ? "manual" : request.getPurpose();
        if (!purpose.equals("manual") && !purpose.equals("interception") && !purpose.equals("question")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown Spark request purpose"));
        }
        if (purpose.equals("question") && (request.getQuestion() == null || request.getQuestion().isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("error", "A question is required"));
        }
        String email = auth.getName();
        UserProgressDto progress = userService.getProgress(email);
        String today = LocalDate.now().toString();
        boolean manual = purpose.equals("manual");
        int count = today.equals(progress.getDailyGeneratedDate()) ? progress.getDailyGeneratedCount() : 0;
        if (manual && count >= (progress.isPro() ? 999 : 3)) {
            return ResponseEntity.status(429).body(Map.of("error", "Daily limit reached",
                    "limitReached", true, "isPro", progress.isPro()));
        }
        final SparkTaskDto task;
        try {
            task = groqAIService.generateSpark(request);
        } catch (RuntimeException e) {
            return unavailable();
        }
        if (task == null || task.getTitle() == null || task.getTitle().isBlank()
                || task.getDescription() == null || task.getDescription().isBlank()) return unavailable();
        if (manual) {
            // Only usage fields are written; never overwrite XP/history captured before a slow AI request.
            UserProgressDto usage = new UserProgressDto();
            usage.setStreak(-1);
            usage.setDailyGeneratedCount(count + 1);
            usage.setDailyGeneratedDate(today);
            userService.saveProgress(email, usage);
        }
        return ResponseEntity.ok(Map.of("task", task));
    }

    @PostMapping("/wormhole")
    public ResponseEntity<?> wormhole(@RequestBody WormholeRequest request, Authentication auth) {
        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "A question is required"));
        }
        String email = auth.getName();
        UserProgressDto progress = userService.getProgress(email);
        final String answer;
        try {
            answer = groqAIService.generateWormholeAnswer(request);
        } catch (RuntimeException e) {
            return unavailable();
        }
        if (answer == null || answer.isBlank()) return unavailable();
        String today = LocalDate.now().toString();
        int count = today.equals(progress.getDailyWormholeDate()) ? progress.getDailyWormholeCount() : 0;
        UserProgressDto usage = new UserProgressDto();
        usage.setStreak(-1);
        usage.setDailyWormholeCount(count + 1);
        usage.setDailyWormholeDate(today);
        userService.saveProgress(email, usage);
        return ResponseEntity.ok(Map.of("answer", answer));
    }

    @PostMapping("/questions")
    public ResponseEntity<?> questions(@RequestBody QuestionsRequest request, Authentication auth) {
        userService.getProgress(auth.getName());
        try {
            var questions = groqAIService.generateQuestions(request.seenQuestions());
            if (questions == null || questions.size() != 6) return unavailable();
            return ResponseEntity.ok(Map.of("questions", questions));
        } catch (RuntimeException e) {
            return unavailable();
        }
    }

    private ResponseEntity<?> unavailable() {
        return ResponseEntity.status(503).body(Map.of("error", "AI service unavailable. Please try again later."));
    }
}
