package com.example.campusactivity.security;

import com.example.campusactivity.dto.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public final class JsonLogoutSuccessHandler implements LogoutSuccessHandler {
    private final SecurityJsonResponseWriter responseWriter;

    public JsonLogoutSuccessHandler(
            SecurityJsonResponseWriter responseWriter
    ) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void onLogoutSuccess(
            HttpServletRequest _request,
            HttpServletResponse response,
            Authentication _authentication
    ) throws IOException, ServletException {
        responseWriter.writeJson(
                response,
                HttpStatus.OK,
                ApiResponse.ok("退出登录成功", null)
        );
    }
}
