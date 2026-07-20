package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.request.LocationCheckInRequest;
import com.example.demo.dto.request.PasswordCheckInRequest;
import com.example.demo.dto.request.QRCodeCheckInRequest;
import com.example.demo.dto.response.CheckInResponse;
import com.example.demo.dto.response.CheckInSessionResponse;
import com.example.demo.dto.response.CheckInStatsResponse;
import com.example.demo.service.CheckInService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/checkins")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    @PostMapping("/qrcode/session")
    public ApiResponse<CheckInSessionResponse> createQrSession(@Valid @RequestBody SessionRequest request) {
        return ApiResponse.ok(checkInService.createQrSession(request.getActivityId()));
    }

    @PostMapping("/qrcode")
    public ApiResponse<CheckInResponse> checkInByQr(@Valid @RequestBody QRCodeCheckInRequest request) {
        return ApiResponse.ok(checkInService.checkInByQr(request));
    }

    @PostMapping("/location")
    public ApiResponse<CheckInResponse> checkInByLocation(@Valid @RequestBody LocationCheckInRequest request) {
        return ApiResponse.ok(checkInService.checkInByLocation(request));
    }

    @PostMapping("/password/session")
    public ApiResponse<CheckInSessionResponse> createPasswordSession(@Valid @RequestBody SessionRequest request) {
        return ApiResponse.ok(checkInService.createPasswordSession(request.getActivityId()));
    }

    @PostMapping("/password")
    public ApiResponse<CheckInResponse> checkInByPassword(@Valid @RequestBody PasswordCheckInRequest request) {
        return ApiResponse.ok(checkInService.checkInByPassword(request));
    }

    @GetMapping("/mine")
    public ApiResponse<List<CheckInResponse>> mine() {
        return ApiResponse.ok(checkInService.listMine());
    }

    @GetMapping
    public ApiResponse<List<CheckInResponse>> listByActivity(@RequestParam Long activityId) {
        return ApiResponse.ok(checkInService.listByActivity(activityId));
    }

    @GetMapping("/stats")
    public ApiResponse<CheckInStatsResponse> stats(@RequestParam Long activityId) {
        return ApiResponse.ok(checkInService.stats(activityId));
    }

    @Data
    public static class SessionRequest {
        @jakarta.validation.constraints.NotNull(message = "活动ID不能为空")
        private Long activityId;
    }
}
