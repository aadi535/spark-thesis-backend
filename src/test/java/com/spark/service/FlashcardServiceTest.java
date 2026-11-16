package com.spark.service;

import com.spark.dto.SparkTaskDto;
import com.spark.model.Flashcard;
import com.spark.model.User;
import com.spark.repository.FlashcardRepository;
import com.spark.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Flashcard Service Unit Tests with Mockito")
class FlashcardServiceTest {

    @Mock
    private FlashcardRepository flashcardRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroqAIService groqAIService;

    @Mock
    private Sm2AlgorithmService sm2AlgorithmService;

    @InjectMocks
    private FlashcardService flashcardService;

    private User testUser;
    private final String testEmail = "student@turiba.lv";

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setEmail(testEmail);
        testUser.setName("Aditya Raj");
    }

    @Test
    @DisplayName("Should successfully fetch due flashcards for authenticated user")
    void testGetDueFlashcards() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        Flashcard dueCard = new Flashcard();
        dueCard.setUser(testUser);
        dueCard.setQuestion("What is SM-2?");
        dueCard.setAnswer("A spaced repetition algorithm.");
        dueCard.setNextReview(LocalDateTime.now().minusHours(1));

        when(flashcardRepository.findByUserAndNextReviewBeforeOrderByCreatedAtDesc(eq(testUser), any(LocalDateTime.class)))
                .thenReturn(List.of(dueCard));

        List<Flashcard> results = flashcardService.getDueFlashcards(testEmail, false);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("What is SM-2?", results.get(0).getQuestion());
        verify(flashcardRepository, times(1))
                .findByUserAndNextReviewBeforeOrderByCreatedAtDesc(eq(testUser), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Should update flashcard using SM-2 service when user is authorized")
    void testUpdateWithConfidenceSuccess() {
        UUID cardId = UUID.randomUUID();
        Flashcard existingCard = new Flashcard();
        existingCard.setUser(testUser);
        existingCard.setQuestion("Test Q");

        when(flashcardRepository.findById(cardId)).thenReturn(Optional.of(existingCard));
        when(sm2AlgorithmService.calculateNextReview(existingCard, 4)).thenReturn(existingCard);
        when(flashcardRepository.save(existingCard)).thenReturn(existingCard);

        Flashcard updated = flashcardService.updateWithConfidence(testEmail, cardId, 4);

        assertNotNull(updated);
        verify(sm2AlgorithmService, times(1)).calculateNextReview(existingCard, 4);
        verify(flashcardRepository, times(1)).save(existingCard);
    }

    @Test
    @DisplayName("Should reject update if requesting user does not own the flashcard")
    void testUpdateWithConfidenceUnauthorized() {
        UUID cardId = UUID.randomUUID();
        User anotherUser = new User();
        anotherUser.setEmail("other@turiba.lv");

        Flashcard foreignCard = new Flashcard();
        foreignCard.setUser(anotherUser);

        when(flashcardRepository.findById(cardId)).thenReturn(Optional.of(foreignCard));

        assertThrows(RuntimeException.class, () ->
                flashcardService.updateWithConfidence(testEmail, cardId, 5)
        );

        verify(flashcardRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should generate AI flashcards and save to repository")
    void testGenerateAndSaveFlashcards() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        SparkTaskDto task = new SparkTaskDto();
        task.setTitle("Quantum Superposition");
        task.setDescription("Particles can exist in multiple states at once.");
        task.setCategory("PHYSICS");

        when(groqAIService.generateFlashcards(anyString())).thenReturn(List.of(
                Map.of("question", "What is superposition?", "answer", "Existing in multiple states.")
        ));

        when(flashcardRepository.save(any(Flashcard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Flashcard> generated = flashcardService.generateAndSave(testEmail, task);

        assertNotNull(generated);
        assertEquals(1, generated.size());
        assertEquals("What is superposition?", generated.get(0).getQuestion());
        verify(flashcardRepository, times(1)).save(any(Flashcard.class));
    }
}
