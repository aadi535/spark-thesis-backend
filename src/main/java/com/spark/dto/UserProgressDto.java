package com.spark.dto;

import java.util.List;

public class UserProgressDto {
    private String name;
    private int xp;
    private int streak;
    private String lastClaimDate;
    private boolean isPro;
    private String proExpiresAt;
    private boolean couponRedeemed;
    private int dailyGeneratedCount;
    private String dailyGeneratedDate;
    private int dailyClaimCount;
    private String dailyClaimDate;
    private int dailyWormholeCount;
    private String dailyWormholeDate;
    private String theme;
    private List<String> seenTasks;
    private List<CompletedTaskDto> completedTasks;

    public UserProgressDto() {}

    // Getters & Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getXp() { return xp; }
    public void setXp(int xp) { this.xp = xp; }
    public int getStreak() { return streak; }
    public void setStreak(int streak) { this.streak = streak; }
    public String getLastClaimDate() { return lastClaimDate; }
    public void setLastClaimDate(String lastClaimDate) { this.lastClaimDate = lastClaimDate; }
    public boolean isPro() { return isPro; }
    public void setPro(boolean isPro) { this.isPro = isPro; }
    public String getProExpiresAt() { return proExpiresAt; }
    public void setProExpiresAt(String proExpiresAt) { this.proExpiresAt = proExpiresAt; }
    public boolean isCouponRedeemed() { return couponRedeemed; }
    public void setCouponRedeemed(boolean couponRedeemed) { this.couponRedeemed = couponRedeemed; }
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
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public List<String> getSeenTasks() { return seenTasks; }
    public void setSeenTasks(List<String> seenTasks) { this.seenTasks = seenTasks; }
    public List<CompletedTaskDto> getCompletedTasks() { return completedTasks; }
    public void setCompletedTasks(List<CompletedTaskDto> completedTasks) { this.completedTasks = completedTasks; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final UserProgressDto d = new UserProgressDto();
        public Builder name(String v) { d.name = v; return this; }
        public Builder xp(int v) { d.xp = v; return this; }
        public Builder streak(int v) { d.streak = v; return this; }
        public Builder lastClaimDate(String v) { d.lastClaimDate = v; return this; }
        public Builder isPro(boolean v) { d.isPro = v; return this; }
        public Builder proExpiresAt(String v) { d.proExpiresAt = v; return this; }
        public Builder couponRedeemed(boolean v) { d.couponRedeemed = v; return this; }
        public Builder dailyGeneratedCount(int v) { d.dailyGeneratedCount = v; return this; }
        public Builder dailyGeneratedDate(String v) { d.dailyGeneratedDate = v; return this; }
        public Builder dailyClaimCount(int v) { d.dailyClaimCount = v; return this; }
        public Builder dailyClaimDate(String v) { d.dailyClaimDate = v; return this; }
        public Builder dailyWormholeCount(int v) { d.dailyWormholeCount = v; return this; }
        public Builder dailyWormholeDate(String v) { d.dailyWormholeDate = v; return this; }
        public Builder theme(String v) { d.theme = v; return this; }
        public Builder seenTasks(List<String> v) { d.seenTasks = v; return this; }
        public Builder completedTasks(List<CompletedTaskDto> v) { d.completedTasks = v; return this; }
        public UserProgressDto build() { return d; }
    }

    public static class CompletedTaskDto {
        private String completionSource;
        public String getCompletionSource() { return completionSource; }
        public void setCompletionSource(String source) { completionSource = source; }
        private String id;
        private long timestamp;
        private String summary;
        private String emoji;
        private List<String> steps;
        private String funFact;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getEmoji() { return emoji; }
        public void setEmoji(String emoji) { this.emoji = emoji; }
        public List<String> getSteps() { return steps; }
        public void setSteps(List<String> steps) { this.steps = steps; }
        public String getFunFact() { return funFact; }
        public void setFunFact(String funFact) { this.funFact = funFact; }

        private String title;
        private String category;
        private int xp;
        private String date;
        private String journal;

        public CompletedTaskDto() {}
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public int getXp() { return xp; }
        public void setXp(int xp) { this.xp = xp; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getJournal() { return journal; }
        public void setJournal(String journal) { this.journal = journal; }
    }
}
