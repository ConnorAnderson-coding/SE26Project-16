package com.example.campusactivity.service.clustering;

import java.util.Objects;

public record ExcludedUserDiagnostic(
        String userId,
        String code,
        String message
) {
    public ExcludedUserDiagnostic {
        Objects.requireNonNull(code, "code 不能为空");
        Objects.requireNonNull(message, "message 不能为空");
    }
}
