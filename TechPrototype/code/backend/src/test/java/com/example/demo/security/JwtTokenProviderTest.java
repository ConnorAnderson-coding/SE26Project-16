package com.example.demo.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    public void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "campus-activity-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm",
                3600000L);
    }

    @Test
    void generateAndValidateToken() {
        String token = jwtTokenProvider.generateToken("524030910001");
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals("524030910001", jwtTokenProvider.getUserId(token));
    }

    @Test
    void invalidTokenShouldFailValidation() {
        assertFalse(jwtTokenProvider.validateToken("invalid.token.value"));
    }
}
