package com.spark.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    private String name;
    private String image;

    // ── Gamification ──
    @Column(name = "total_xp")
    private int totalXp = 0;

    private int streak = 0;

    @Column(name = "last_claim_date")
    private String lastClaimDate;

    // ── Daily Limits ──
    @Column(name = "daily_generated_count")
    private int dailyGeneratedCount = 0;

    @Column(name = "daily_generated_date")
    private String dailyGeneratedDate;

    @Column(name = "daily_claim_count")
    private int dailyClaimCount = 0;

    @Column(name = "daily_claim_date")
    private String dailyClaimDate;

    @Column(name = "daily_wormhole_count")
    private int dailyWormholeCount = 0;

    @Column(name = "daily_wormhole_date")
    private String dailyWormholeDate;

    // ── Subscription ──
    @Column(name = "is_pro")
    private boolean pro = false;

    @Column(name = "pro_expires_at")
    private LocalDateTime proExpiresAt;

    @Column(name = "coupon_redeemed")
    private boolean couponRedeemed = false;

    @Column(name = "stripe_customer_id", unique = true)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", unique = true)
    private String stripeSubscriptionId;

    // ── Preferences ──
    private String theme = "default";

    // ── Task History ──
    @Column(columnDefinition = "TEXT")
    private String seenTasks;

    @Column(name = "completed_tasks", columnDefinition = "TEXT")
    private String completedTasks;

    // ── Timestamps ──
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Relationships ──
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Flashcard> flashcards;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewAttempt> reviewAttempts;

    // ── Constructors ──
    public User() {}

    // ── Getters & Setters ──
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public int getTotalXp() { return totalXp; }
    public void setTotalXp(int totalXp) { this.totalXp = totalXp; }

    public int getStreak() { return streak; }
    public void setStreak(int streak) { this.streak = streak; }

    public String getLastClaimDate() { return lastClaimDate; }
    public void setLastClaimDate(String lastClaimDate) { this.lastClaimDate = lastClaimDate; }

    public int getDailyGeneratedCount() { return dailyGeneratedCount; }
    public void setDailyGeneratedCount(int dailyGeneratedCount) { this.dailyGeneratedCount = dailyGeneratedCount; }

    public String getDailyGeneratedDate() { return dailyGeneratedDate; }
    public void setDailyGeneratedDate(String dailyGeneratedDate) { this.dailyGeneratedDate = dailyGeneratedDate; }

    public int getDailyClaimCount() { return dailyClaimCount; }
    public void setDailyClaimCount(int dailyClaimCount) { this.dailyClaimCount = dailyClaimCount; }

    public String getDailyClaimDate() { return dailyClaimDate; }
    public void setDailyClaimDate(String dailyClaimDate) { this.dailyClaimDate = dailyClaimDate; }

    public int getDailyWormholeCount() { return dailyWormholeCount; }
    public void setDailyWormholeCount(int dailyWormholeCount) { this.dailyWormholeCount = dailyWormholeCount; }

    public String getDailyWormholeDate() { return dailyWormholeDate; }
    public void setDailyWormholeDate(String dailyWormholeDate) { this.dailyWormholeDate = dailyWormholeDate; }

    public boolean isPro() { return pro; }
    public void setPro(boolean pro) { this.pro = pro; }

    public LocalDateTime getProExpiresAt() { return proExpiresAt; }
    public void setProExpiresAt(LocalDateTime proExpiresAt) { this.proExpiresAt = proExpiresAt; }

    public boolean isCouponRedeemed() { return couponRedeemed; }
    public void setCouponRedeemed(boolean couponRedeemed) { this.couponRedeemed = couponRedeemed; }

    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }

    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public void setStripeSubscriptionId(String stripeSubscriptionId) { this.stripeSubscriptionId = stripeSubscriptionId; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public String getSeenTasks() { return seenTasks; }
    public void setSeenTasks(String seenTasks) { this.seenTasks = seenTasks; }

    public String getCompletedTasks() { return completedTasks; }
    public void setCompletedTasks(String completedTasks) { this.completedTasks = completedTasks; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<Flashcard> getFlashcards() { return flashcards; }

    // Builder pattern (replaces Lombok @Builder)
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final User user = new User();
        public Builder email(String email) { user.email = email; return this; }
        public Builder name(String name) { user.name = name; return this; }
        public Builder image(String image) { user.image = image; return this; }
        public Builder pro(boolean pro) { user.pro = pro; return this; }
        public User build() { return user; }
    }
}
