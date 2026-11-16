package com.spark.controller;

import com.spark.dto.UserProgressDto;
import com.spark.dto.LeaderboardResponse;
import com.spark.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/progress")
    public ResponseEntity<UserProgressDto> getProgress(Authentication auth) {
        return ResponseEntity.ok(userService.getProgress(auth.getName()));
    }

    @PostMapping("/progress")
    public ResponseEntity<UserProgressDto> saveProgress(@RequestBody UserProgressDto dto, Authentication auth) {
        return ResponseEntity.ok(userService.saveProgress(auth.getName(), dto));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, String>> deleteUser(Authentication auth) {
        userService.deleteUser(auth.getName());
        return ResponseEntity.ok(Map.of("message", "Account deleted"));
    }

    @PostMapping("/redeem")
    public ResponseEntity<Map<String, Object>> redeemCoupon(@RequestBody Map<String, String> body, Authentication auth) {
        String result = userService.redeemCoupon(auth.getName(), body.getOrDefault("code", ""));
        return switch (result) {
            case "SUCCESS" -> ResponseEntity.ok(Map.of("success", true, "message", "30 days of Pro activated!"));
            case "ALREADY_USED" -> ResponseEntity.badRequest().body(Map.of("success", false, "message", "Coupon already redeemed"));
            default -> ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid coupon code"));
        };
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<LeaderboardResponse> leaderboard(Authentication auth) {
        return ResponseEntity.ok(userService.getLeaderboard(auth.getName()));
    }
}
