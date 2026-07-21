package com.example.campusactivity.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 64) String id,
        @NotBlank @Size(max = 128) String password
) {
    @Override
    public String toString() {
        return "LoginRequest[redacted]";
    }
}
