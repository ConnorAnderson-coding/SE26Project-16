package com.example.campusactivity.security;

import com.example.campusactivity.dto.security.SecurityErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public final class SecurityJsonResponseWriter {
    private final ObjectMapper objectMapper;

    public SecurityJsonResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void writeError(
            HttpServletResponse response,
            SecurityErrorCode errorCode
    ) throws IOException {
        writeJson(
                response,
                errorCode.status(),
                SecurityErrorResponse.from(errorCode)
        );
    }

    public void writeJson(
            HttpServletResponse response,
            HttpStatus status,
            Object body
    ) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
