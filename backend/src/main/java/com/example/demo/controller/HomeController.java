package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.response.HomeStatsResponse;
import com.example.demo.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/stats")
    public ApiResponse<HomeStatsResponse> stats() {
        return ApiResponse.ok(homeService.getStats());
    }
}
