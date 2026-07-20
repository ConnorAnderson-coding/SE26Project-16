package com.example.campusactivity.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public final class JsonAccessDeniedHandler implements AccessDeniedHandler {
    private final SecurityJsonResponseWriter responseWriter;

    public JsonAccessDeniedHandler(SecurityJsonResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void handle(
            HttpServletRequest _request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException, ServletException {
        SecurityErrorCode errorCode =
                exception instanceof MissingCsrfTokenException
                        || exception instanceof InvalidCsrfTokenException
                        ? SecurityErrorCode.CSRF_TOKEN_INVALID
                        : SecurityErrorCode.ACCESS_DENIED;
        responseWriter.writeError(response, errorCode);
    }
}
