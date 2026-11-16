package com.spark.controller;

import com.spark.dto.SparkTaskDto;
import com.spark.model.Flashcard;
import com.spark.service.FlashcardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/flashcards")
public class FlashcardController {

    private final FlashcardService flashcardService;

    public FlashcardController(FlashcardService flashcardService) {
        this.flashcardService = flashcardService;
    }

    @PostMapping
    public ResponseEntity<List<Flashcard>> generate(@RequestBody SparkTaskDto task, Authentication auth) {
        return ResponseEntity.ok(flashcardService.generateAndSave(auth.getName(), task));
    }

    @GetMapping
    public ResponseEntity<List<Flashcard>> getDue(@RequestParam(defaultValue = "false") boolean all, Authentication auth) {
        return ResponseEntity.ok(flashcardService.getDueFlashcards(auth.getName(), all));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Flashcard> update(@PathVariable UUID id,
                                             @RequestBody Map<String, Integer> body,
                                             Authentication auth) {
        return ResponseEntity.ok(flashcardService.updateWithConfidence(auth.getName(), id, body.getOrDefault("confidence", 1)));
    }
}
