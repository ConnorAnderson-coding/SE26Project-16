package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.request.RegistrationRequest;
import com.example.demo.dto.request.ReviewRegistrationRequest;
import com.example.demo.dto.response.RegistrationResponse;
import com.example.demo.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/registrations")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping
    public ApiResponse<RegistrationResponse> signup(@Valid @RequestBody RegistrationRequest request) {
        return ApiResponse.ok(registrationService.signup(request));
    }

    @GetMapping("/mine")
    public ApiResponse<List<RegistrationResponse>> mine() {
        return ApiResponse.ok(registrationService.getMine());
    }

    @GetMapping
    public ApiResponse<List<RegistrationResponse>> list(
            @RequestParam(required = false) Long activityId) {
        return ApiResponse.ok(registrationService.listForOrganizer(activityId));
    }

    @PutMapping("/{id}/review")
    public ApiResponse<RegistrationResponse> review(
            @PathVariable Long id,
            @Valid @RequestBody ReviewRegistrationRequest request) {
        return ApiResponse.ok(registrationService.review(id, request.getApproved()));
    }

    @GetMapping("/status")
    public ApiResponse<String> status(@RequestParam Long activityId) {
        return ApiResponse.ok(registrationService.getSignupStatus(activityId));
    }
}
