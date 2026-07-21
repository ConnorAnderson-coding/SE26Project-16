package com.example.campusactivity.security;

import org.springframework.security.core.AuthenticationException;

public final class InvalidAuthRequestAuthenticationException
        extends AuthenticationException {
    public InvalidAuthRequestAuthenticationException() {
        super("认证请求无效");
    }
}
