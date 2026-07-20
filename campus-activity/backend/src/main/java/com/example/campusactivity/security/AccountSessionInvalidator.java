package com.example.campusactivity.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;

@Component
public final class AccountSessionInvalidator {
    private final LogoutHandler logoutHandler = new CompositeLogoutHandler(
            new SecurityContextLogoutHandler(),
            new CookieClearingLogoutHandler("JSESSIONID")
    );

    public void invalidate(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        logoutHandler.logout(request, response, authentication);
    }
}
