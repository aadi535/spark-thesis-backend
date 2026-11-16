package com.spark.controller;

import com.spark.dto.ReviewDtos.*;
import com.spark.service.ReviewService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {
    private final ReviewService reviews;
    public ReviewController(ReviewService reviews) { this.reviews = reviews; }

    @PostMapping("/start")
    public Started start(Authentication auth) { return reviews.start(auth.getName()); }

    @PostMapping("/{id}/submit")
    public Result submit(Authentication auth, @PathVariable UUID id, @RequestBody Submission submission) {
        return reviews.submit(auth.getName(), id, submission);
    }

    @GetMapping("/history")
    public History history(Authentication auth) { return reviews.history(auth.getName()); }
}
