package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.request.FeedbackRequest;
import com.example.demo.dto.response.FeedbackResponse;
import com.example.demo.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ApiResponse<FeedbackResponse> submit(@Valid @RequestBody FeedbackRequest request) {
        return ApiResponse.ok(feedbackService.submit(request));
    }

    @GetMapping("/mine")
    public ApiResponse<List<FeedbackResponse>> mine() {
        return ApiResponse.ok(feedbackService.getMine());
    }

    @GetMapping
    public ApiResponse<List<FeedbackResponse>> list(@RequestParam Long activityId) {
        return ApiResponse.ok(feedbackService.listByActivity(activityId));
    }
}
