package com.example.campusactivity.dto.auth;

import java.util.List;

public record AuthenticatedUserResponse(
        String id,
        String name,
        String role,
        String college,
        String grade,
        List<String> interests,
        List<String> availableTime
) {
    public AuthenticatedUserResponse {
        interests = interests == null ? List.of() : List.copyOf(interests);
        availableTime = availableTime == null ? List.of() : List.copyOf(availableTime);
    }
}
