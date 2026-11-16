package com.spark.service;

import com.spark.model.Flashcard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SM-2 Spaced Repetition Algorithm Unit Tests")
class Sm2AlgorithmServiceTest {

    private Sm2AlgorithmService sm2Service;

    @BeforeEach
    void setUp() {
        sm2Service = new Sm2AlgorithmService();
    }

    @Test
    @DisplayName("Should schedule review for tomorrow on memory lapse (confidence < 3)")
    void testMemoryLapseResetsIntervalToOneDay() {
        Flashcard card = new Flashcard();
        card.setReps(3);
        card.setStability(2.5);
        card.setState(2);

        Flashcard result = sm2Service.calculateNextReview(card, 2);

        assertEquals(4, result.getReps(), "Reps should increment");
        assertEquals(1, result.getLapses(), "Lapse count should increment");
        assertEquals(3, result.getState(), "State should transition to Relearning (3)");
        assertTrue(result.getNextReview().isAfter(LocalDateTime.now().plusHours(23)), "Next review should be ~1 day away");
        assertTrue(result.getNextReview().isBefore(LocalDateTime.now().plusHours(25)), "Next review should not exceed 25 hours");
    }

    @Test
    @DisplayName("Should increase stability and schedule extended interval on perfect recall (confidence = 5)")
    void testSuccessfulRecallIncreasesIntervalAndStability() {
        Flashcard card = new Flashcard();
        card.setReps(2);
        card.setStability(2.5);

        Flashcard result = sm2Service.calculateNextReview(card, 5);

        assertEquals(3, result.getReps());
        assertEquals(0, result.getLapses());
        assertEquals(2, result.getState(), "State should transition to Review (2)");
        assertTrue(result.getStability() >= 2.5, "Stability should increase or stay high");
        assertTrue(result.getNextReview().isAfter(LocalDateTime.now().plusDays(2)), "Next review interval should be multi-day");
    }

    @Test
    @DisplayName("Should clamp stability to MIN_STABILITY (1.3) even after multiple lapses")
    void testMinimumStabilityClamping() {
        Flashcard card = new Flashcard();
        card.setStability(1.1);

        Flashcard result = sm2Service.calculateNextReview(card, 1);

        assertTrue(result.getStability() >= Sm2AlgorithmService.MIN_STABILITY,
                "Stability must never drop below minimum threshold 1.3");
    }

    @Test
    @DisplayName("First successful repetition should schedule exactly 1 day interval")
    void testFirstRepetitionInterval() {
        Flashcard card = new Flashcard();
        card.setReps(0);

        Flashcard result = sm2Service.calculateNextReview(card, 4);

        assertEquals(1, result.getReps());
        assertTrue(result.getNextReview().isAfter(LocalDateTime.now().plusHours(23)));
        assertTrue(result.getNextReview().isBefore(LocalDateTime.now().plusHours(25)));
    }
}
