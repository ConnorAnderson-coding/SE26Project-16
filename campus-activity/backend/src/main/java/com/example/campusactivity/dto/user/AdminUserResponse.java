package com.example.campusactivity.dto.user;

import java.util.List;

public record AdminUserResponse(
        String id,
        String name,
        String role,
        String college,
        String grade,
        List<String> interests,
        List<String> availableTime
) {
    public AdminUserResponse {
        interests = interests == null ? List.of() : List.copyOf(interests);
        availableTime = availableTime == null ? List.of() : List.copyOf(availableTime);
    }
}
