package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.response.ActivityResponse;
import com.example.demo.dto.response.FavoriteStatusResponse;
import com.example.demo.dto.response.FavoriteToggleResponse;
import com.example.demo.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public ApiResponse<List<ActivityResponse>> list() {
        return ApiResponse.ok(favoriteService.getMine());
    }

    @PostMapping("/{activityId}")
    public ApiResponse<FavoriteToggleResponse> toggle(@PathVariable Long activityId) {
        return ApiResponse.ok(favoriteService.toggle(activityId));
    }

    @GetMapping("/{activityId}/status")
    public ApiResponse<FavoriteStatusResponse> status(@PathVariable Long activityId) {
        return ApiResponse.ok(favoriteService.getStatus(activityId));
    }
}
