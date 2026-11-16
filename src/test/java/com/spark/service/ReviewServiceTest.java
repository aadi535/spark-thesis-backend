package com.spark.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spark.dto.ReviewDtos.*;
import com.spark.dto.UserProgressDto;
import com.spark.dto.UserProgressDto.CompletedTaskDto;
import com.spark.model.ReviewAttempt;
import com.spark.model.User;
import com.spark.repository.ReviewAttemptRepository;
import com.spark.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewServiceTest {
    private final UserService users = mock(UserService.class);
    private final UserRepository accounts = mock(UserRepository.class);
    private final ReviewAttemptRepository attempts = mock(ReviewAttemptRepository.class);
    private final GroqAIService ai = mock(GroqAIService.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private ReviewService service;
    private UserProgressDto progress;
    private ReviewAttempt saved;
    private final String email = "learner@example.com";

    static List<CompletedTaskDto> entries(int count) {
        return IntStream.range(0, count).mapToObj(i -> {
            var entry = new CompletedTaskDto();
            entry.setId("source-" + i);
            entry.setTitle("Explored idea " + i);
            entry.setJournal("This lesson explains a useful mechanism with a clear familiar example, number " + i);
            entry.setCompletionSource("interception_explore");
            return entry;
        }).toList();
    }
    static GeneratedQuestion question(String id, int number) {
        return new GeneratedQuestion(id, "Which explanation matches learning mechanism " + number + "?",
                List.of("The correct mechanism " + number, "A plausible alternative", "A different process", "An unrelated mechanism"),
                0, "The lesson describes the first mechanism using a familiar example.");
    }
    @BeforeEach void setup() {
        progress = new UserProgressDto();
        progress.setCompletedTasks(new ArrayList<>(entries(5)));
        when(users.getProgress(email)).thenReturn(progress);
        when(accounts.findByEmail(email)).thenReturn(Optional.of(User.builder().email(email).name("Learner").build()));
        when(attempts.save(any())).thenAnswer(call -> {
            saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.fromString("00000000-0000-0000-0000-000000000001"));
            return saved;
        });
        when(ai.generateReview(anyList())).thenAnswer(call -> {
            List<CompletedTaskDto> selected = call.getArgument(0);
            return IntStream.range(0, 5).mapToObj(i -> question(selected.get(i).getId(), i)).toList();
        });
        service = new ReviewService(users, accounts, attempts, ai, new AIContentValidator(), json);
    }

    @Test void startUsesOneBatchCallAndNeverReturnsAnswerKey() throws Exception {
        var started = service.start(email);
        assertEquals(5, started.questions().size());
        assertEquals(5, started.questions().stream().map(Question::sourceEntryId).distinct().count());
        verify(ai, times(1)).generateReview(anyList());
        String publicJson = json.writeValueAsString(started);
        assertFalse(publicJson.contains("correctOptionIndex"));
        assertFalse(publicJson.contains("\"explanation\":"));
        assertFalse(publicJson.contains(email));
        assertNotNull(saved.getCreatedAt());
        assertNull(saved.getCompletedAt());
        verify(users, never()).saveProgress(anyString(), any());
    }

    @Test void onlyMarkedDistinctEntriesWithContentQualify() {
        var list = new ArrayList<>(entries(7));
        list.get(0).setCompletionSource(null);
        list.get(1).setCompletionSource("manual");
        list.get(2).setJournal(" ");
        list.add(list.get(3));
        progress.setCompletedTasks(list);
        when(attempts.findTop20ByUserEmailAndCompletedAtNotNullOrderByCompletedAtDesc(email)).thenReturn(List.of());
        assertEquals(4, service.history(email).eligibleCount());
        var error = assertThrows(ResponseStatusException.class, () -> service.start(email));
        assertEquals(409, error.getStatusCode().value());
        verifyNoInteractions(ai);
    }

    @Test void missingHistoryIsNotEligible() {
        progress.setCompletedTasks(null);
        assertThrows(ResponseStatusException.class, () -> service.start(email));
        verifyNoInteractions(ai);
    }

    @Test void shufflingKeepsCorrectAnswersAndServerSavesFourOutOfFive() throws Exception {
        var started = service.start(email);
        List<StoredQuestion> stored = json.readValue(saved.getQuestions(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        List<Integer> answers = new ArrayList<>();
        for (var item : stored) {
            assertTrue(item.options().get(item.correctOptionIndex()).startsWith("The correct mechanism"));
            answers.add(item.correctOptionIndex());
        }
        answers.set(0, (answers.getFirst() + 1) % 4);
        when(attempts.findByIdAndUserEmail(started.id(), email)).thenReturn(Optional.of(saved));
        var result = service.submit(email, started.id(), new Submission(answers));
        assertEquals(4, result.correctCount());
        assertEquals(5, result.totalCount());
        assertNotNull(result.completedAt());
        assertEquals(answers.getFirst(), result.questions().getFirst().selectedOptionIndex());
        assertFalse(result.questions().getFirst().explanation().isBlank());
        verify(users, never()).saveProgress(anyString(), any());
    }

    @Test void duplicateSubmissionReturnsOriginalResult() {
        var started = service.start(email);
        when(attempts.findByIdAndUserEmail(started.id(), email)).thenReturn(Optional.of(saved));
        var first = service.submit(email, started.id(), new Submission(List.of(0, 0, 0, 0, 0)));
        var repeated = service.submit(email, started.id(), new Submission(List.of(1, 1, 1, 1, 1)));
        assertEquals(first, repeated);
        verify(attempts, times(2)).save(any()); // start plus first submission only
    }

    @Test void otherAccountCannotSubmitAttempt() {
        var started = service.start(email);
        when(attempts.findByIdAndUserEmail(started.id(), "other@example.com")).thenReturn(Optional.empty());
        var error = assertThrows(ResponseStatusException.class, () -> service.submit("other@example.com", started.id(),
                new Submission(List.of(0, 0, 0, 0, 0))));
        assertEquals(404, error.getStatusCode().value());
        assertNull(saved.getCompletedAt());
    }

    @Test void invalidSubmissionsDoNotCompleteAttempt() {
        var started = service.start(email);
        when(attempts.findByIdAndUserEmail(started.id(), email)).thenReturn(Optional.of(saved));
        for (var answers : List.of(List.of(0), List.of(0, 0, 0, 0, 4), Arrays.asList(0, 0, 0, 0, null))) {
            assertThrows(ResponseStatusException.class, () -> service.submit(email, started.id(), new Submission(answers)));
        }
        assertNull(saved.getCompletedAt());
    }

    @Test void invalidOrForeignGeneratedContentIsNeverSaved() {
        doReturn(IntStream.range(0, 5).mapToObj(i -> question("foreign-" + i, i)).toList()).when(ai).generateReview(anyList());
        var error = assertThrows(ResponseStatusException.class, () -> service.start(email));
        assertEquals(503, error.getStatusCode().value());
        verify(attempts, never()).save(any());
    }

    @Test void providerFailureIsControlledAndDoesNotUseUnrelatedFallback() {
        doThrow(new IllegalStateException("Provider failure")).when(ai).generateReview(anyList());
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> service.start(email)).getStatusCode().value());
        verify(attempts, never()).save(any());
    }

    @Test void historyUsesOnlyOwnerScopedCompletedQueryAndIncludesSavedFeedback() {
        var started = service.start(email);
        when(attempts.findByIdAndUserEmail(started.id(), email)).thenReturn(Optional.of(saved));
        service.submit(email, started.id(), new Submission(List.of(0, 0, 0, 0, 0)));
        when(attempts.findTop20ByUserEmailAndCompletedAtNotNullOrderByCompletedAtDesc(email)).thenReturn(List.of(saved));
        var history = service.history(email);
        assertEquals(1, history.results().size());
        assertEquals(5, history.results().getFirst().questions().size());
        verify(attempts).findTop20ByUserEmailAndCompletedAtNotNullOrderByCompletedAtDesc(email);
    }
}
