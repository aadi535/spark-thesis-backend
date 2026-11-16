package com.spark.repository;

import com.spark.model.ReviewAttempt;
import com.spark.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

// A dedicated disposable PostgreSQL database is required; never use the application database.
@EnabledIfEnvironmentVariable(named = "SPARK_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
@DataJpaTest(properties = {"spring.datasource.url=${SPARK_TEST_DATABASE_URL}",
        "spring.datasource.username=${SPARK_TEST_DATABASE_USER:postgres}",
        "spring.datasource.password=${SPARK_TEST_DATABASE_PASSWORD:}", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReviewAttemptPersistenceTest {
    @Autowired TestEntityManager entityManager;
    @Autowired UserRepository users;
    @Autowired ReviewAttemptRepository attempts;

    private ReviewAttempt attempt(User owner, boolean complete) {
        var item = new ReviewAttempt();
        item.setUser(owner);
        item.setQuestions("[]");
        if (complete) {
            item.setCompletedAt(Instant.now());
            item.setAnswers("[0,1,2,3,0]");
            item.setCorrectCount(4);
        }
        return attempts.saveAndFlush(item);
    }
    @Test void databaseStoresDatedResultsAndScopesHistoryByOwnerAndCompletion() {
        var owner = users.saveAndFlush(User.builder().email("owner@test.local").name("Owner").build());
        var other = users.saveAndFlush(User.builder().email("other@test.local").name("Other").build());
        var complete = attempt(owner, true);
        attempt(owner, false);
        attempt(other, true);
        entityManager.clear();
        var history = attempts.findTop20ByUserEmailAndCompletedAtNotNullOrderByCompletedAtDesc(owner.getEmail());
        assertEquals(1, history.size());
        assertEquals(complete.getId(), history.getFirst().getId());
        assertEquals(4, history.getFirst().getCorrectCount());
        assertNotNull(history.getFirst().getCompletedAt());
        assertTrue(attempts.findByIdAndUserEmail(complete.getId(), other.getEmail()).isEmpty());
    }
    @Test void deletingAccountCascadesToAttemptsWithoutDeletingAnotherUsersHistory() {
        var owner = users.saveAndFlush(User.builder().email("owner@test.local").build());
        var other = users.saveAndFlush(User.builder().email("other@test.local").build());
        var a = attempt(owner, true);
        var b = attempt(other, true);
        entityManager.clear();
        users.delete(users.findById(owner.getId()).orElseThrow());
        users.flush();
        entityManager.clear();
        assertTrue(attempts.findById(a.getId()).isEmpty());
        assertTrue(attempts.findById(b.getId()).isPresent());
    }
}
