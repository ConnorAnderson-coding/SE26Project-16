package com.example.campusactivity.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public final class JsonAuthenticationEntryPoint
        implements AuthenticationEntryPoint {
    private final SecurityJsonResponseWriter responseWriter;

    public JsonAuthenticationEntryPoint(
            SecurityJsonResponseWriter responseWriter
    ) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest _request,
            HttpServletResponse response,
            AuthenticationException _exception
    ) throws IOException, ServletException {
        responseWriter.writeError(
                response,
                SecurityErrorCode.AUTHENTICATION_REQUIRED
        );
    }
}
