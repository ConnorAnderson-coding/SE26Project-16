package com.example.campusactivity.dto.user;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminUpdateUserRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) String college,
        @Size(max = 100) String grade,
        @Size(max = 50) List<@NotBlank @Size(max = 64) String> interests,
        @Size(max = 50) List<@NotBlank @Size(max = 64) String> availableTime
) {
    public AdminUpdateUserRequest {
        interests = interests == null ? List.of() : List.copyOf(interests);
        availableTime = availableTime == null ? List.of() : List.copyOf(availableTime);
    }

    @JsonAnySetter
    public void rejectUnknownProperty(String _name, Object _value) {
        throw new IllegalArgumentException("用户请求无效");
    }
}
