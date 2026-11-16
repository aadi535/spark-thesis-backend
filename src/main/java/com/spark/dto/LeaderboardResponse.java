package com.spark.dto;

import java.util.List;

public record LeaderboardResponse(List<Entry> entries, Entry currentUser) {
    public record Entry(Long position, String name, int xp, boolean currentUser) {}
}
