package com.example.campusactivity.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException, ServletException {
        boolean csrfFailure = exception instanceof MissingCsrfTokenException
                || exception instanceof InvalidCsrfTokenException;
        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        boolean anonymousClusteringSubmission = csrfFailure
                && !authenticated
                && "POST".equals(request.getMethod())
                && "/api/v1/admin/community-clustering/runs".equals(
                        request.getRequestURI()
                );
        SecurityErrorCode errorCode = anonymousClusteringSubmission
                ? SecurityErrorCode.AUTHENTICATION_REQUIRED
                : csrfFailure
                ? SecurityErrorCode.CSRF_TOKEN_INVALID
                : SecurityErrorCode.ACCESS_DENIED;
        responseWriter.writeError(response, errorCode);
    }
}
