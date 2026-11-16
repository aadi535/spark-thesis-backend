package com.spark.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spark.model.User;
import com.spark.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaderboardTest {
    private static final String KEY = "spark:leaderboard:global";
    private final UserRepository users = mock(UserRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ZSetOperations<String, String> scores = mock(ZSetOperations.class);
    private final ObjectMapper json = new ObjectMapper();
    private UserService service;
    private User current;

    @BeforeEach void setup() {
        when(redis.opsForZSet()).thenReturn(scores);
        when(scores.reverseRank(anyString(), anyString())).thenReturn(null);
        current = User.builder().email("current@example.com").name("Current Player").build();
        current.setTotalXp(150);
        when(users.findByEmail(current.getEmail())).thenReturn(Optional.of(current));
        when(users.findAll()).thenReturn(List.of(current));
        service = new UserService(users, redis, json);
    }

    @Test void leaderboardRepopulatesAllDatabaseUsersAndRemovesRestoredStaleMembers() {
        var other = User.builder().email("other@example.com").name("Other Player").build();
        other.setTotalXp(300);
        when(users.findAll()).thenReturn(List.of(current, other));
        when(scores.range(KEY, 0, -1)).thenReturn(Set.of(current.getEmail(), other.getEmail(), "deleted@example.com"));
        when(users.findByEmail(other.getEmail())).thenReturn(Optional.of(other));
        when(scores.reverseRangeWithScores(KEY, 0, 19)).thenReturn(new LinkedHashSet<>(List.of(
                new DefaultTypedTuple<>(other.getEmail(), 300.0),
                new DefaultTypedTuple<>(current.getEmail(), 150.0))));

        var result = service.getLeaderboard(current.getEmail());

        verify(scores).add(KEY, current.getEmail(), 150);
        verify(scores).add(KEY, other.getEmail(), 300);
        verify(scores).remove(KEY, "deleted@example.com");
        assertEquals(2, result.entries().size());
        assertEquals("Other Player", result.entries().getFirst().name());
        assertEquals(1L, result.entries().getFirst().position());
        assertEquals(2L, result.currentUser().position());
        var order = inOrder(scores);
        order.verify(scores).remove(KEY, "deleted@example.com");
        order.verify(scores).reverseRangeWithScores(KEY, 0, 19);
    }

    @Test void orderedTopTwentyIncludesPositionAndHighlightsCurrentUserWithoutEmail() throws Exception {
        var entries = new LinkedHashSet<ZSetOperations.TypedTuple<String>>();
        for (int i = 0; i < 20; i++) {
            String email = i == 1 ? current.getEmail() : "player" + i + "@example.com";
            when(users.findByEmail(email)).thenReturn(Optional.of(i == 1 ? current :
                    User.builder().email(email).name("Player " + i).build()));
            entries.add(new DefaultTypedTuple<>(email, (double) (3000 - i * 100)));
        }
        when(scores.reverseRangeWithScores(KEY, 0, 19)).thenReturn(entries);
        var result = service.getLeaderboard(current.getEmail());
        assertEquals(20, result.entries().size());
        assertEquals(1L, result.entries().getFirst().position());
        assertEquals(3000, result.entries().getFirst().xp());
        assertEquals(2L, result.currentUser().position());
        assertTrue(result.entries().get(1).currentUser());
        assertEquals(1, result.entries().stream().filter(e -> e.currentUser()).count());
        assertFalse(json.writeValueAsString(result).contains("@"));
        verify(scores, never()).reverseRank(anyString(), anyString());
    }

    @Test void currentUserOutsideTopTwentyGetsActualGlobalPositionAndRedisXp() {
        when(scores.reverseRangeWithScores(KEY, 0, 19)).thenReturn(Set.of());
        when(scores.reverseRank(KEY, current.getEmail())).thenReturn(34L);
        when(scores.score(KEY, current.getEmail())).thenReturn(450.0);
        var result = service.getLeaderboard(current.getEmail());
        assertEquals(35L, result.currentUser().position());
        assertEquals(450, result.currentUser().xp());
        assertTrue(result.currentUser().currentUser());
    }

    @Test void emptyLeaderboardDoesNotInventARankOrExposeAnEmailAsName() throws Exception {
        current.setName(current.getEmail());
        when(scores.reverseRangeWithScores(KEY, 0, 19)).thenReturn(Set.of());
        var result = service.getLeaderboard(current.getEmail());
        assertTrue(result.entries().isEmpty());
        assertNull(result.currentUser().position());
        assertEquals("Explorer", result.currentUser().name());
        assertFalse(json.writeValueAsString(result).contains("@"));
    }

    @Test void redisFailureIsNotReportedAsAnEmptyLeaderboard() {
        when(scores.reverseRangeWithScores(KEY, 0, 19)).thenThrow(new IllegalStateException("Unavailable"));
        assertThrows(IllegalStateException.class, () -> service.getLeaderboard(current.getEmail()));
    }
}
