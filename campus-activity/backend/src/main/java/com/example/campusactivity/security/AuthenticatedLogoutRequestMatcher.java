package com.example.campusactivity.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

public final class AuthenticatedLogoutRequestMatcher
        implements RequestMatcher {
    private final RequestMatcher pathMatcher =
            new AntPathRequestMatcher("/api/auth/logout", "POST");

    @Override
    public boolean matches(HttpServletRequest request) {
        if (!pathMatcher.matches(request)) {
            return false;
        }
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
