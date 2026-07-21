package com.example.campusactivity.dto.auth;

import java.util.Objects;

public record CsrfTokenResponse(
        String token,
        String headerName,
        String parameterName
) {
    public CsrfTokenResponse {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(headerName, "headerName");
        Objects.requireNonNull(parameterName, "parameterName");
    }
}
