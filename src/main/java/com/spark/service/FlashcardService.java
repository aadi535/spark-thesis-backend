package com.spark.service;

import com.spark.dto.SparkTaskDto;
import com.spark.model.Flashcard;
import com.spark.model.User;
import com.spark.repository.FlashcardRepository;
import com.spark.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FlashcardService {

    private static final Logger log = LoggerFactory.getLogger(FlashcardService.class);

    private final FlashcardRepository flashcardRepository;
    private final UserRepository userRepository;
    private final GroqAIService groqAIService;
    private final Sm2AlgorithmService sm2AlgorithmService;

    public FlashcardService(FlashcardRepository flashcardRepository,
                            UserRepository userRepository,
                            GroqAIService groqAIService,
                            Sm2AlgorithmService sm2AlgorithmService) {
        this.flashcardRepository = flashcardRepository;
        this.userRepository = userRepository;
        this.groqAIService = groqAIService;
        this.sm2AlgorithmService = sm2AlgorithmService;
    }

    @Transactional
    public List<Flashcard> generateAndSave(String email, SparkTaskDto task) {
        User user = findUser(email);
        String content = String.format(
                "Title: %s\nHook: %s\nDescription: %s\nFun Fact: %s",
                task.getTitle(), task.getHook(), task.getDescription(), task.getFunFact());

        List<Map<String, String>> generated = groqAIService.generateFlashcards(content);
        List<Flashcard> saved = new ArrayList<>();

        long dbStart = System.currentTimeMillis();
        for (Map<String, String> card : generated) {
            Flashcard flashcard = Flashcard.builder()
                    .user(user)
                    .question(card.getOrDefault("question", ""))
                    .answer(card.getOrDefault("answer", ""))
                    .category(task.getCategory())
                    .build();
            saved.add(flashcardRepository.save(flashcard));
        }
        log.info("[BENCHMARK] PostgreSQL saved {} flashcards in {} ms", saved.size(), (System.currentTimeMillis() - dbStart));
        log.info("Generated {} flashcards for user: {}", saved.size(), email);
        return saved;
    }

    public List<Flashcard> getDueFlashcards(String email, boolean all) {
        User user = findUser(email);
        long start = System.currentTimeMillis();
        List<Flashcard> results;
        if (all) {
            results = flashcardRepository.findByUserOrderByCreatedAtDesc(user);
        } else {
            results = flashcardRepository.findByUserAndNextReviewBeforeOrderByCreatedAtDesc(user, LocalDateTime.now());
        }
        log.info("[BENCHMARK] PostgreSQL query for due flashcards took {} ms (count: {})", (System.currentTimeMillis() - start), results.size());
        return results;
    }

    @Transactional
    public Flashcard updateWithConfidence(String email, UUID flashcardId, int confidence) {
        long fetchStart = System.currentTimeMillis();
        Flashcard card = flashcardRepository.findById(flashcardId)
                .orElseThrow(() -> new RuntimeException("Flashcard not found"));
        log.info("[BENCHMARK] PostgreSQL fetch flashcard by ID took {} ms", (System.currentTimeMillis() - fetchStart));

        if (!card.getUser().getEmail().equals(email)) {
            throw new RuntimeException("Unauthorized");
        }

        // Delegate to isolated SM-2 Algorithm Service
        Flashcard updatedCard = sm2AlgorithmService.calculateNextReview(card, confidence);

        long saveStart = System.currentTimeMillis();
        Flashcard persisted = flashcardRepository.save(updatedCard);
        log.info("[BENCHMARK] PostgreSQL save updated flashcard took {} ms", (System.currentTimeMillis() - saveStart));

        return persisted;
    }

    private User findUser(String email) {
        long start = System.currentTimeMillis();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
        log.info("[BENCHMARK] PostgreSQL find user by email took {} ms", (System.currentTimeMillis() - start));
        return user;
    }
}
