package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.PageResult;
import com.example.demo.dto.request.ActivityRequest;
import com.example.demo.dto.response.ActivityResponse;
import com.example.demo.service.ActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping
    public ApiResponse<PageResult<ActivityResponse>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(activityService.list(category, status, location, keyword, page, size, sort));
    }

    @GetMapping("/recommended")
    public ApiResponse<List<ActivityResponse>> recommended(
            @RequestParam(defaultValue = "6") int limit) {
        return ApiResponse.ok(activityService.getRecommended(limit));
    }

    @GetMapping("/mine")
    public ApiResponse<List<ActivityResponse>> mine() {
        return ApiResponse.ok(activityService.getMine());
    }

    @GetMapping("/{id}")
    public ApiResponse<ActivityResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(activityService.getById(id));
    }

    @PostMapping
    public ApiResponse<ActivityResponse> create(@Valid @RequestBody ActivityRequest request) {
        return ApiResponse.ok(activityService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ActivityResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ActivityRequest request) {
        return ApiResponse.ok(activityService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        activityService.delete(id);
        return ApiResponse.ok();
    }
}
