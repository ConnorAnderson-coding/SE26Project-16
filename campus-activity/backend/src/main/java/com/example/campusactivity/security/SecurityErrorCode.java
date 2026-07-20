package com.example.campusactivity.security;

import org.springframework.http.HttpStatus;

public enum SecurityErrorCode {
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "请先登录"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "无权访问该资源"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "账号或密码错误"),
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "请求安全校验失败"),
    INVALID_AUTH_REQUEST(HttpStatus.BAD_REQUEST, "认证请求无效"),
    ACCOUNT_ALREADY_EXISTS(HttpStatus.CONFLICT, "该账号已存在"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");

    private final HttpStatus status;
    private final String safeMessage;

    SecurityErrorCode(HttpStatus status, String safeMessage) {
        this.status = status;
        this.safeMessage = safeMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String safeMessage() {
        return safeMessage;
    }
}
