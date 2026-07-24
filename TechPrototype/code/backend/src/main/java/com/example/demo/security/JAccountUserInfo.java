package com.example.demo.security;

public record JAccountUserInfo(
        String sub,
        String name,
        String code,
        String type) {
}
