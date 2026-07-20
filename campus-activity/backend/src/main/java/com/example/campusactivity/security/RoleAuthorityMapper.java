package com.example.campusactivity.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public final class RoleAuthorityMapper {
    public String normalize(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("角色无效");
        }
        String normalized = role.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "admin", "teacher", "student" -> normalized;
            default -> throw new IllegalArgumentException("角色无效");
        };
    }

    public List<GrantedAuthority> authoritiesFor(String role) {
        String normalized = normalize(role);
        String authority = switch (normalized) {
            case "admin" -> "ROLE_ADMIN";
            case "teacher" -> "ROLE_TEACHER";
            case "student" -> "ROLE_STUDENT";
            default -> throw new IllegalStateException("角色映射无效");
        };
        return List.of(new SimpleGrantedAuthority(authority));
    }
}
