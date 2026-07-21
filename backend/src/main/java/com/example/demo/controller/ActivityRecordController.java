package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.request.ActivityRecordRequest;
import com.example.demo.dto.response.ActivityRecordResponse;
import com.example.demo.service.ActivityRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/activities/{activityId}/record")
@RequiredArgsConstructor
public class ActivityRecordController {

    private final ActivityRecordService activityRecordService;

    @GetMapping
    public ApiResponse<ActivityRecordResponse> get(@PathVariable Long activityId) {
        return ApiResponse.ok(activityRecordService.getByActivityId(activityId));
    }

    @PostMapping
    public ApiResponse<ActivityRecordResponse> publish(
            @PathVariable Long activityId,
            @Valid @RequestBody ActivityRecordRequest request) {
        return ApiResponse.ok(activityRecordService.publish(activityId, request));
    }
}
