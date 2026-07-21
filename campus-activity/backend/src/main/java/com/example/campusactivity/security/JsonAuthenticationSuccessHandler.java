package com.example.campusactivity.security;

import com.example.campusactivity.dto.ApiResponse;
import com.example.campusactivity.dto.auth.AuthenticatedUserResponse;
import com.example.campusactivity.service.auth.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
public final class JsonAuthenticationSuccessHandler
        implements AuthenticationSuccessHandler {
    private final AuthService authService;
    private final AccountSessionInvalidator sessionInvalidator;
    private final SecurityJsonResponseWriter responseWriter;

    public JsonAuthenticationSuccessHandler(
            AuthService authService,
            AccountSessionInvalidator sessionInvalidator,
            SecurityJsonResponseWriter responseWriter
    ) {
        this.authService = authService;
        this.sessionInvalidator = sessionInvalidator;
        this.responseWriter = responseWriter;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        Optional<AuthenticatedUserResponse> user =
                authService.findAuthenticatedUser(authentication.getName());
        if (user.isEmpty()) {
            sessionInvalidator.invalidate(request, response, authentication);
            responseWriter.writeError(
                    response,
                    SecurityErrorCode.AUTHENTICATION_REQUIRED
            );
            return;
        }
        responseWriter.writeJson(
                response,
                HttpStatus.OK,
                ApiResponse.ok("登录成功", user.orElseThrow())
        );
    }
}
