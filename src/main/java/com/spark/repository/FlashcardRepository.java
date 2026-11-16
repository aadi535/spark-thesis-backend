package com.spark.repository;

import com.spark.model.Flashcard;
import com.spark.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface FlashcardRepository extends JpaRepository<Flashcard, UUID> {
    List<Flashcard> findByUserOrderByCreatedAtDesc(User user);
    List<Flashcard> findByUserAndNextReviewBeforeOrderByCreatedAtDesc(User user, LocalDateTime now);
    long countByUser(User user);
}
