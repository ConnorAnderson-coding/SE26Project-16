package com.example.campusactivity.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public final class JsonAuthenticationFailureHandler
        implements AuthenticationFailureHandler {
    private final SecurityJsonResponseWriter responseWriter;

    public JsonAuthenticationFailureHandler(
            SecurityJsonResponseWriter responseWriter
    ) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest _request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        SecurityErrorCode errorCode =
                exception instanceof InvalidAuthRequestAuthenticationException
                        ? SecurityErrorCode.INVALID_AUTH_REQUEST
                        : SecurityErrorCode.INVALID_CREDENTIALS;
        responseWriter.writeError(response, errorCode);
    }
}
