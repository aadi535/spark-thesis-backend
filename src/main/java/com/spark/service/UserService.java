package com.spark.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spark.dto.UserProgressDto;
import com.spark.dto.LeaderboardResponse;
import com.spark.model.User;
import com.spark.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final String LEADERBOARD_KEY = "spark:leaderboard:global";

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public UserService(UserRepository userRepository, StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public UserProgressDto getProgress(String email) {
        User user = findUser(email);
        if (user.isPro() && user.getProExpiresAt() != null
                && user.getProExpiresAt().isBefore(LocalDateTime.now())) {
            user.setPro(false);
            user.setProExpiresAt(null);
            userRepository.save(user);
            log.info("Pro expired for user: {}", email);
        }
        return mapToDto(user);
    }

    @Transactional
    public UserProgressDto saveProgress(String email, UserProgressDto dto) {
        User user = userRepository.findByEmailForUpdate(email).orElseThrow(this::missingAccount);

        if (dto.getName() != null && !dto.getName().isBlank()) user.setName(dto.getName().trim());
        if (dto.getXp() > 0) user.setTotalXp(dto.getXp());
        if (dto.getStreak() >= 0) user.setStreak(dto.getStreak());
        if (dto.getLastClaimDate() != null) user.setLastClaimDate(dto.getLastClaimDate());
        if (dto.getDailyClaimDate() != null) {
            user.setDailyClaimDate(dto.getDailyClaimDate());
            user.setDailyClaimCount(dto.getDailyClaimCount());
        }
        if (dto.getDailyGeneratedDate() != null) {
            user.setDailyGeneratedDate(dto.getDailyGeneratedDate());
            user.setDailyGeneratedCount(dto.getDailyGeneratedCount());
        }
        if (dto.getDailyWormholeDate() != null) {
            user.setDailyWormholeDate(dto.getDailyWormholeDate());
            user.setDailyWormholeCount(dto.getDailyWormholeCount());
        }
        if (dto.getTheme() != null) user.setTheme(dto.getTheme());

        if (dto.getSeenTasks() != null) {
            try { user.setSeenTasks(objectMapper.writeValueAsString(dto.getSeenTasks())); }
            catch (Exception e) { log.warn("Failed to serialize seenTasks"); }
        }
        if (dto.getCompletedTasks() != null && !dto.getCompletedTasks().isEmpty()) {
            // History is append-only here: a fresh/stale client must never erase saved records.
            var history = new java.util.LinkedHashMap<String, UserProgressDto.CompletedTaskDto>();
            for (var task : readCompletedTasks(user)) history.put(task.getId(), task);
            for (var task : dto.getCompletedTasks()) {
                if (task == null || task.getTitle() == null || task.getTitle().isBlank()) continue;
                ensureHistoryId(task);
                history.putIfAbsent(task.getId(), task);
                if ("interception_explore".equals(task.getCompletionSource())) {
                    history.get(task.getId()).setCompletionSource("interception_explore");
                }
            }
            try { user.setCompletedTasks(objectMapper.writeValueAsString(history.values())); }
            catch (Exception e) { throw new IllegalStateException("Could not save learning history", e); }
        }

        userRepository.save(user);
        syncLeaderboard(user);
        return mapToDto(user);
    }

    @Transactional
    public void deleteUser(String email) {
        // Use the same account lock as progress saves. JPA cascades deletion to flashcards.
        userRepository.findByEmailForUpdate(email).ifPresent(userRepository::delete);
        userRepository.flush();
        try {
            redisTemplate.opsForZSet().remove(LEADERBOARD_KEY, email);
        } catch (RuntimeException e) {
            log.warn("Leaderboard cleanup unavailable during account deletion");
        }
    }

    @Transactional
    public void activatePro(String email, int days) {
        User user = findUser(email);
        user.setPro(true);
        user.setProExpiresAt(LocalDateTime.now().plusDays(days));
        userRepository.save(user);
        log.info("Pro activated for {} days for user: {}", days, email);
    }

    @Transactional
    public String redeemCoupon(String email, String code) {
        if (!"TRYSPARK30".equalsIgnoreCase(code.trim())) return "INVALID";
        User user = findUser(email);
        if (user.isCouponRedeemed()) return "ALREADY_USED";
        user.setCouponRedeemed(true);
        user.setPro(true);
        user.setProExpiresAt(LocalDateTime.now().plusDays(30));
        userRepository.save(user);
        return "SUCCESS";
    }

    public LeaderboardResponse getLeaderboard(String email) {
        User currentUser = findUser(email);
        rebuildLeaderboard();
        long redisStart = System.currentTimeMillis();
        var entries = redisTemplate.opsForZSet().reverseRangeWithScores(LEADERBOARD_KEY, 0, 19);
        log.info("[BENCHMARK] Redis reverseRangeWithScores took {} ms", (System.currentTimeMillis() - redisStart));

        List<LeaderboardResponse.Entry> result = new ArrayList<>();
        long position = 0;
        if (entries != null) for (var entry : entries) {
            long entryPosition = ++position;
            String memberEmail = entry.getValue();
            userRepository.findByEmail(memberEmail).ifPresent(user -> {
                result.add(new LeaderboardResponse.Entry(entryPosition, leaderboardName(user),
                        entry.getScore().intValue(), email.equals(memberEmail)));
            });
        }
        var ownEntry = result.stream().filter(LeaderboardResponse.Entry::currentUser).findFirst().orElse(null);
        if (ownEntry == null) {
            Long rank = redisTemplate.opsForZSet().reverseRank(LEADERBOARD_KEY, email);
            Double score = rank == null ? null : redisTemplate.opsForZSet().score(LEADERBOARD_KEY, email);
            ownEntry = new LeaderboardResponse.Entry(rank == null ? null : rank + 1,
                    leaderboardName(currentUser), score == null ? currentUser.getTotalXp() : score.intValue(), true);
        }
        return new LeaderboardResponse(result, ownEntry);
    }

    private void rebuildLeaderboard() {
        var users = userRepository.findAll();
        var currentEmails = new java.util.HashSet<String>();
        var scores = redisTemplate.opsForZSet();
        for (User user : users) {
            currentEmails.add(user.getEmail());
            scores.add(LEADERBOARD_KEY, user.getEmail(), user.getTotalXp());
        }
        var members = scores.range(LEADERBOARD_KEY, 0, -1);
        if (members != null) {
            for (String member : members) {
                if (!currentEmails.contains(member)) scores.remove(LEADERBOARD_KEY, member);
            }
        }
    }

    private String leaderboardName(User user) {
        String name = user.getName();
        return name == null || name.isBlank() || name.contains("@") ? "Explorer" : name.trim();
    }

    private void syncLeaderboard(User user) {
        long redisStart = System.currentTimeMillis();
        try {
            redisTemplate.opsForZSet().add(LEADERBOARD_KEY, user.getEmail(), user.getTotalXp());
            log.info("[BENCHMARK] Redis opsForZSet.add took {} ms for user: {}", (System.currentTimeMillis() - redisStart), user.getEmail());
        } catch (Exception e) {
            log.warn("Failed to sync leaderboard for {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private org.springframework.web.server.ResponseStatusException missingAccount() {
        return new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Spark account no longer exists. Sign in again.");
    }

    private User findUser(String email) {
        long dbStart = System.currentTimeMillis();
        User user = userRepository.findByEmail(email).orElseThrow(this::missingAccount);
        log.info("[BENCHMARK] PostgreSQL findUser took {} ms for email: {}", (System.currentTimeMillis() - dbStart), email);
        return user;
    }

    private List<UserProgressDto.CompletedTaskDto> readCompletedTasks(User user) {
        if (user.getCompletedTasks() == null || user.getCompletedTasks().isBlank()) return new ArrayList<>();
        try {
            List<UserProgressDto.CompletedTaskDto> tasks = objectMapper.readValue(
                    user.getCompletedTasks(), new TypeReference<>() {});
            if (tasks == null) return new ArrayList<>();
            tasks.removeIf(java.util.Objects::isNull);
            tasks.forEach(this::ensureHistoryId);
            return tasks;
        } catch (Exception e) {
            // Do not replace unreadable saved history with a new partial list.
            throw new IllegalStateException("Could not read learning history", e);
        }
    }

    private void ensureHistoryId(UserProgressDto.CompletedTaskDto task) {
        if (task.getId() == null || task.getId().isBlank()) {
            String legacyKey = task.getTitle() + "|" + task.getCategory() + "|" + task.getDate() + "|" + task.getJournal();
            task.setId(java.util.UUID.nameUUIDFromBytes(legacyKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
        }
    }

    private UserProgressDto mapToDto(User user) {
        List<String> seenTasks = new ArrayList<>();
        try {
            if (user.getSeenTasks() != null)
                seenTasks = objectMapper.readValue(user.getSeenTasks(), new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse user JSON fields for {}", user.getEmail());
        }

        UserProgressDto dto = new UserProgressDto();
        dto.setName(user.getName());
        dto.setXp(user.getTotalXp());
        dto.setStreak(user.getStreak());
        dto.setLastClaimDate(user.getLastClaimDate());
        dto.setPro(user.isPro());
        dto.setProExpiresAt(user.getProExpiresAt() != null ? user.getProExpiresAt().toString() : null);
        dto.setCouponRedeemed(user.isCouponRedeemed());
        dto.setDailyGeneratedCount(user.getDailyGeneratedCount());
        dto.setDailyGeneratedDate(user.getDailyGeneratedDate());
        dto.setDailyClaimCount(user.getDailyClaimCount());
        dto.setDailyClaimDate(user.getDailyClaimDate());
        dto.setDailyWormholeCount(user.getDailyWormholeCount());
        dto.setDailyWormholeDate(user.getDailyWormholeDate());
        dto.setTheme(user.getTheme());
        dto.setSeenTasks(seenTasks);
        dto.setCompletedTasks(readCompletedTasks(user));
        return dto;
    }
}
