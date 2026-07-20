package com.example.campusactivity.controller;

import com.example.campusactivity.dto.ApiResponse;
import com.example.campusactivity.dto.auth.AuthenticatedUserResponse;
import com.example.campusactivity.dto.auth.CsrfTokenResponse;
import com.example.campusactivity.dto.auth.RegistrationRequest;
import com.example.campusactivity.dto.security.SecurityErrorResponse;
import com.example.campusactivity.security.AccountSessionInvalidator;
import com.example.campusactivity.security.SecurityErrorCode;
import com.example.campusactivity.service.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final AccountSessionInvalidator sessionInvalidator;

    public AuthController(
            AuthService authService,
            AccountSessionInvalidator sessionInvalidator
    ) {
        this.authService = authService;
        this.sessionInvalidator = sessionInvalidator;
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        String token = csrfToken.getToken();
        return new CsrfTokenResponse(
                token,
                csrfToken.getHeaderName(),
                csrfToken.getParameterName()
        );
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthenticatedUserResponse>> register(
            @Valid @RequestBody RegistrationRequest request
    ) {
        AuthenticatedUserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("注册成功", user));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Optional<AuthenticatedUserResponse> user =
                authService.findAuthenticatedUser(authentication.getName());
        if (user.isEmpty()) {
            sessionInvalidator.invalidate(request, response, authentication);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(SecurityErrorResponse.from(
                            SecurityErrorCode.AUTHENTICATION_REQUIRED
                    ));
        }
        return ResponseEntity.ok(ApiResponse.ok(user.orElseThrow()));
    }
}
