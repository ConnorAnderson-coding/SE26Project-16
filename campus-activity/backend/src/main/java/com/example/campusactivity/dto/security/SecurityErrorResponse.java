package com.example.campusactivity.dto.security;

import com.example.campusactivity.security.SecurityErrorCode;

import java.util.Map;

public record SecurityErrorResponse(
        String code,
        String message,
        Map<String, Object> details
) {
    public SecurityErrorResponse {
        details = Map.of();
    }

    public static SecurityErrorResponse from(SecurityErrorCode errorCode) {
        return new SecurityErrorResponse(
                errorCode.name(),
                errorCode.safeMessage(),
                Map.of()
        );
    }
}
