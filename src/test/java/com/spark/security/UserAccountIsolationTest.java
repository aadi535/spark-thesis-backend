package com.spark.security;

import com.spark.config.SecurityConfig;
import com.spark.controller.UserController;
import com.spark.model.User;
import com.spark.repository.UserRepository;
import com.spark.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses the real controller, JWT filter and user service; database/network dependencies are isolated. */
@WebMvcTest(controllers = UserController.class, properties = {
        "app.jwt.secret=account-isolation-test-only-secret-at-least-32-bytes",
        "app.jwt.expiration-ms=60000", "google.client-id=test-client"})
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtUtil.class, UserService.class})
class UserAccountIsolationTest {
    @Test void exploreSourcePersistsWithoutGuessingLegacyHistoryOrRemovingExistingContent() throws Exception {
        a.setCompletedTasks("[{\"id\":\"legacy\",\"title\":\"An older idea\",\"journal\":\"Original learning content\"}]");
        mvc.perform(get("/api/v1/user/progress").header("Authorization", "Bearer " + jwt.generateToken("a@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completedTasks[0].completionSource").isEmpty());
        mvc.perform(post("/api/v1/user/progress").header("Authorization", "Bearer " + jwt.generateToken("a@example.com"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"completedTasks\":[{\"id\":\"legacy\",\"title\":\"An older idea\",\"completionSource\":\"interception_explore\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completedTasks[0].completionSource").value("interception_explore"))
                .andExpect(jsonPath("$.completedTasks[0].journal").value("Original learning content"));
        mvc.perform(post("/api/v1/user/progress").header("Authorization", "Bearer " + jwt.generateToken("a@example.com"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"completedTasks\":[{\"id\":\"legacy\",\"title\":\"An older idea\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completedTasks[0].completionSource").value("interception_explore"));
    }
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @MockBean UserRepository users;
    @MockBean StringRedisTemplate redis;
    private User a;
    private User b;

    @BeforeEach void accounts() {
        a = User.builder().email("a@example.com").name("Account A").build();
        a.setTotalXp(150);
        a.setStreak(2);
        b = User.builder().email("b@example.com").name("Account B").build();
        b.setTotalXp(900);
        b.setStreak(6);
        when(users.findByEmail("a@example.com")).thenReturn(Optional.of(a));
        when(users.findByEmail("b@example.com")).thenReturn(Optional.of(b));
        when(users.findByEmailForUpdate("a@example.com")).thenReturn(Optional.of(a));
        when(users.findByEmailForUpdate("b@example.com")).thenReturn(Optional.of(b));
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void displayNameEditChangesOnlyAuthenticatedAccountAndPreservesProgress() throws Exception {
        mvc.perform(post("/api/v1/user/progress")
                .header("Authorization", "Bearer " + jwt.generateToken("a@example.com"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  Chosen Name  \",\"xp\":0,\"streak\":-1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Chosen Name"));
        assertEquals("Chosen Name", a.getName());
        assertEquals("Account B", b.getName());
        assertEquals(150, a.getTotalXp());
        assertEquals(2, a.getStreak());
    }

    @Test void eachJwtReadsOnlyItsOwnProgressEvenWithAnotherEmailInQuery() throws Exception {
        mvc.perform(get("/api/v1/user/progress").param("email", "b@example.com")
                .header("Authorization", "Bearer " + jwt.generateToken("a@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Account A"))
                .andExpect(jsonPath("$.xp").value(150)).andExpect(jsonPath("$.streak").value(2));
        mvc.perform(get("/api/v1/user/progress").param("email", "a@example.com")
                .header("Authorization", "Bearer " + jwt.generateToken("b@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Account B"))
                .andExpect(jsonPath("$.xp").value(900)).andExpect(jsonPath("$.streak").value(6));
    }

    @Test void aCannotTargetBsProgressUsingBodyOrQuery() throws Exception {
        mvc.perform(post("/api/v1/user/progress").param("email", "b@example.com")
                .header("Authorization", "Bearer " + jwt.generateToken("a@example.com"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"b@example.com\",\"xp\":300,\"streak\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Account A"));
        assertEquals(300, a.getTotalXp());
        assertEquals(3, a.getStreak());
        assertEquals(900, b.getTotalXp());
        assertEquals(6, b.getStreak());
        verify(users, never()).save(b);
    }

    @Test void bCannotTargetAsProgress() throws Exception {
        mvc.perform(post("/api/v1/user/progress")
                .header("Authorization", "Bearer " + jwt.generateToken("b@example.com"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@example.com\",\"xp\":1000,\"streak\":7}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Account B"));
        assertEquals(150, a.getTotalXp());
        assertEquals(2, a.getStreak());
        assertEquals(1000, b.getTotalXp());
        verify(users, never()).save(a);
    }

    private void saveHistory(String email, String history) throws Exception {
        mvc.perform(post("/api/v1/user/progress")
                .header("Authorization", "Bearer " + jwt.generateToken(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"xp\":300,\"streak\":2,\"completedTasks\":" + history + "}"))
                .andExpect(status().isOk());
    }

    private static final String JOURNAL = """
        [{"id":"entry-1","timestamp":1791200000000,"title":"Gravity","category":"SCIENCE",
          "xp":150,"date":"Oct 05, 2026","journal":"Full learning content","summary":"A summary",
          "emoji":"planet","steps":["Observe","Explain"],"funFact":"A fact"}]
        """;

    @Test void historyRoundTripsWithoutDuplicateEntriesOrAwardingXpAgain() throws Exception {
        saveHistory("a@example.com", JOURNAL);
        saveHistory("a@example.com", JOURNAL);
        assertTrue(a.getCompletedTasks().contains("Full learning content"));
        mvc.perform(get("/api/v1/user/progress")
                .header("Authorization", "Bearer " + jwt.generateToken("a@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xp").value(300))
                .andExpect(jsonPath("$.completedTasks.length()").value(1))
                .andExpect(jsonPath("$.completedTasks[0].id").value("entry-1"))
                .andExpect(jsonPath("$.completedTasks[0].timestamp").value(1791200000000L))
                .andExpect(jsonPath("$.completedTasks[0].title").value("Gravity"))
                .andExpect(jsonPath("$.completedTasks[0].category").value("SCIENCE"))
                .andExpect(jsonPath("$.completedTasks[0].xp").value(150))
                .andExpect(jsonPath("$.completedTasks[0].date").value("Oct 05, 2026"))
                .andExpect(jsonPath("$.completedTasks[0].journal").value("Full learning content"))
                .andExpect(jsonPath("$.completedTasks[0].summary").value("A summary"))
                .andExpect(jsonPath("$.completedTasks[0].emoji").value("planet"))
                .andExpect(jsonPath("$.completedTasks[0].steps[1]").value("Explain"))
                .andExpect(jsonPath("$.completedTasks[0].funFact").value("A fact"));
        assertEquals(300, a.getTotalXp());
    }

    @Test void emptyAndStaleHistoryCannotEraseSavedEntries() throws Exception {
        saveHistory("a@example.com", JOURNAL);
        saveHistory("a@example.com", "[]");
        saveHistory("a@example.com", "null");
        saveHistory("a@example.com", "[{\"id\":\"entry-2\",\"title\":\"Light\",\"xp\":150}]");
        saveHistory("a@example.com", JOURNAL);
        mvc.perform(get("/api/v1/user/progress")
                .header("Authorization", "Bearer " + jwt.generateToken("a@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completedTasks.length()").value(2));
        verify(users, atLeastOnce()).findByEmailForUpdate("a@example.com");
    }

    @Test void learningHistoryIsOwnedByJwtAndLegacyEntriesHaveStableIds() throws Exception {
        a.setCompletedTasks("[{\"title\":\"Old entry\",\"date\":\"2026-01-01\",\"journal\":\"Old content\"}]");
        saveHistory("a@example.com", JOURNAL);
        String saved = a.getCompletedTasks();
        saveHistory("a@example.com", JOURNAL);
        assertEquals(saved, a.getCompletedTasks());
        mvc.perform(get("/api/v1/user/progress").param("email", "a@example.com")
                .header("Authorization", "Bearer " + jwt.generateToken("b@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completedTasks.length()").value(0));
        saveHistory("b@example.com", JOURNAL);
        assertEquals(saved, a.getCompletedTasks());
    }

    @Test void deletionUsesJwtOwnerAndRedisFailureDoesNotBlockDatabaseDeletion() throws Exception {
        var sortedSet = mock(org.springframework.data.redis.core.ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(sortedSet);
        when(sortedSet.remove("spark:leaderboard:global", "a@example.com"))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("unavailable"));
        mvc.perform(delete("/api/v1/user/delete").param("email", "b@example.com")
                .header("Authorization", "Bearer " + jwt.generateToken("a@example.com")))
                .andExpect(status().isOk());
        verify(users).findByEmailForUpdate("a@example.com");
        verify(users).delete(a);
        verify(users).flush();
        verify(users, never()).delete(b);
        verify(users, never()).save(any());
        verify(sortedSet).remove("spark:leaderboard:global", "a@example.com");
    }

    @Test void oldJwtCannotRecreateDeletedAccountThroughProgressReadsOrWrites() throws Exception {
        doAnswer(invocation -> {
            when(users.findByEmail("a@example.com")).thenReturn(Optional.empty());
            when(users.findByEmailForUpdate("a@example.com")).thenReturn(Optional.empty());
            return null;
        }).when(users).delete(a);
        String authorization = "Bearer " + jwt.generateToken("a@example.com");
        mvc.perform(delete("/api/v1/user/delete").header("Authorization", authorization))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/user/progress").header("Authorization", authorization))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/user/progress").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{\"xp\":900,\"completedTasks\":" + JOURNAL + "}"))
                .andExpect(status().isUnauthorized());
        verify(users, never()).save(any());
    }

    @Test void repeatedDeletionOfMissingAccountIsSafe() throws Exception {
        when(users.findByEmailForUpdate("a@example.com")).thenReturn(Optional.empty());
        mvc.perform(delete("/api/v1/user/delete")
                .header("Authorization", "Bearer " + jwt.generateToken("a@example.com")))
                .andExpect(status().isOk());
        verify(users, never()).delete(any(User.class));
        verify(users, never()).save(any());
    }

    @Test void accountDeletionRequiresAuthentication() throws Exception {
        mvc.perform(delete("/api/v1/user/delete")).andExpect(status().isUnauthorized());
        verify(users, never()).delete(any(User.class));
    }
}
