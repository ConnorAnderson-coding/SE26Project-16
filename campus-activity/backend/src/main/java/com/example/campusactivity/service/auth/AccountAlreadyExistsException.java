package com.example.campusactivity.service.auth;

public final class AccountAlreadyExistsException extends RuntimeException {
    public AccountAlreadyExistsException() {
        super("账号已存在");
    }
}
