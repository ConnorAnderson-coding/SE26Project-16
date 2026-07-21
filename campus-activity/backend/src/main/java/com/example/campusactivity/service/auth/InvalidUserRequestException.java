package com.example.campusactivity.service.auth;

public final class InvalidUserRequestException extends RuntimeException {
    public InvalidUserRequestException() {
        super("用户请求无效");
    }
}
