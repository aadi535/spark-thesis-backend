package com.spark.repository;

import com.spark.model.ReviewAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewAttemptRepository extends JpaRepository<ReviewAttempt, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReviewAttempt> findByIdAndUserEmail(UUID id, String email);
    List<ReviewAttempt> findTop20ByUserEmailAndCompletedAtNotNullOrderByCompletedAtDesc(String email);
}
