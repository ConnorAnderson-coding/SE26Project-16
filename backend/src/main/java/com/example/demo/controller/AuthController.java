package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.BusinessException;
import com.example.demo.dto.request.LoginRequest;
import com.example.demo.dto.request.RegisterRequest;
import com.example.demo.dto.response.AuthResponse;
import com.example.demo.config.JAccountProperties;
import com.example.demo.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class AuthController {

    private static final String JACCOUNT_STATE_COOKIE = "JACCOUNT_OAUTH_STATE";

    private final AuthService authService;
    private final JAccountProperties jAccountProperties;

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @GetMapping("/jaccount/login")
    public ResponseEntity<Void> jAccountLogin() {
        String state = UUID.randomUUID().toString();
        URI location = authService.buildJAccountAuthorizationUri(state);
        log.info("jAccount login redirect issued: stateLength={}, locationHost={}",
                state.length(), location.getHost());
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location.toString())
                .header(HttpHeaders.SET_COOKIE, buildStateCookie(state).toString())
                .build();
    }

    @GetMapping("/jaccount/callback")
    public ResponseEntity<Void> jAccountCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @CookieValue(value = JACCOUNT_STATE_COOKIE, required = false) String savedState) {
        URI location;
        log.info("jAccount callback received: codeLength={}, statePresent={}, savedStatePresent={}, stateMatches={}",
                code == null ? -1 : code.length(), state != null, savedState != null,
                state != null && state.equals(savedState));
        try {
            AuthResponse response = authService.loginWithJAccount(code, state, savedState);
            location = buildFrontendCallback("token", response.getToken());
        } catch (BusinessException ex) {
            log.warn("jAccount callback failed: {}", ex.getMessage());
            location = buildFrontendCallback("error", ex.getMessage());
        } catch (Exception ex) {
            log.warn("jAccount callback failed unexpectedly", ex);
            location = buildFrontendCallback("error", ex.getMessage());
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location.toString())
                .header(HttpHeaders.SET_COOKIE, clearStateCookie().toString())
                .build();
    }

    @GetMapping("/jaccount/logout")
    public ResponseEntity<Void> jAccountLogout() {
        URI location;
        try {
            location = authService.buildJAccountLogoutUri(UUID.randomUUID().toString());
        } catch (BusinessException ex) {
            location = URI.create(jAccountProperties.getFrontendLogoutUri());
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location.toString())
                .build();
    }

    private URI buildFrontendCallback(String key, String value) {
        String fragment = UriComponentsBuilder.newInstance()
                .queryParam(key, value)
                .build()
                .encode()
                .toUriString()
                .substring(1);
        return URI.create(jAccountProperties.getFrontendCallbackUri() + "#" + fragment);
    }

    private ResponseCookie buildStateCookie(String state) {
        return ResponseCookie.from(JACCOUNT_STATE_COOKIE, state)
                .httpOnly(true)
                .secure(jAccountProperties.isStateCookieSecure())
                .sameSite("Lax")
                .path("/api/v1/auth/jaccount")
                .maxAge(Duration.ofMinutes(5))
                .build();
    }

    private ResponseCookie clearStateCookie() {
        return ResponseCookie.from(JACCOUNT_STATE_COOKIE, "")
                .httpOnly(true)
                .secure(jAccountProperties.isStateCookieSecure())
                .sameSite("Lax")
                .path("/api/v1/auth/jaccount")
                .maxAge(Duration.ZERO)
                .build();
    }
}
