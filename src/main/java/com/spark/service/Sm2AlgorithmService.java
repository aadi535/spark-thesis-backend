package com.spark.service;

import com.spark.model.Flashcard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * SuperMemo-2 (SM-2) Spaced Repetition Algorithm Implementation.
 * Isolated component for thesis evaluation, UML modeling, and unit testing.
 *
 * Mathematical Model:
 *  - Quality Rating (q): 1 (hard/forgot) to 5 (perfect recall).
 *  - Ease Factor (EF / Stability): Multiplier determining interval progression.
 *  - Interval (I_n): Next review interval in days.
 *
 * Algorithm State Transitions:
 *  - If q < 3: Failure / Lapse -> interval reset to 1 day, state = 3 (Relearning).
 *  - If q >= 3: Success -> interval scaled by stability multiplier, state = 2 (Review).
 */
@Service
public class Sm2AlgorithmService {

    private static final Logger log = LoggerFactory.getLogger(Sm2AlgorithmService.class);

    public static final double MIN_STABILITY = 1.3;
    public static final double DEFAULT_STABILITY = 2.5;

    /**
     * Calculates and updates the review schedule of a flashcard using the SM-2 algorithm.
     *
     * @param card       the target Flashcard entity
     * @param confidence user-reported confidence score (1 to 5)
     * @return updated Flashcard entity ready for persistence
     */
    public Flashcard calculateNextReview(Flashcard card, int confidence) {
        long startTime = System.nanoTime();

        int q = Math.max(1, Math.min(5, confidence));
        card.setReps(card.getReps() + 1);

        if (q < 3) {
            // Memory Lapse: reset interval, decrease stability
            card.setLapses(card.getLapses() + 1);
            card.setState(3); // 3 = Relearning
            card.setStability(Math.max(card.getStability() * 0.8, MIN_STABILITY));
            card.setNextReview(LocalDateTime.now().plusDays(1));

            log.debug("SM-2 [LAPSE] Card ID: {}, Reps: {}, Stability: {}, Next Review: +1 day",
                    card.getId(), card.getReps(), card.getStability());
        } else {
            // Successful Recall: adjust ease factor / stability
            double currentStability = card.getStability() <= 0 ? DEFAULT_STABILITY : card.getStability();

            // SM-2 Ease Factor calculation formula:
            // EF' = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
            double delta = 0.1 - (5 - q) * (0.08 + (5 - q) * 0.02);
            double newStability = Math.max(currentStability + delta, MIN_STABILITY);
            card.setStability(newStability);

            // Interval determination based on consecutive repetitions
            int intervalDays;
            if (card.getReps() == 1) {
                intervalDays = 1;
            } else if (card.getReps() == 2) {
                intervalDays = 3;
            } else {
                intervalDays = (int) Math.ceil(card.getReps() * newStability);
            }

            card.setState(2); // 2 = Review state
            card.setNextReview(LocalDateTime.now().plusDays(intervalDays));

            log.debug("SM-2 [SUCCESS] Card ID: {}, Reps: {}, Quality: {}, Interval: {} days, New Stability: {}",
                    card.getId(), card.getReps(), q, intervalDays, newStability);
        }

        long durationMicros = (System.nanoTime() - startTime) / 1000;
        log.info("[BENCHMARK] SM-2 calculation completed in {} us", durationMicros);

        return card;
    }
}
