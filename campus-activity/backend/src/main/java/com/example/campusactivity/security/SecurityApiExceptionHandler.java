package com.example.campusactivity.security;

import com.example.campusactivity.controller.AuthController;
import com.example.campusactivity.controller.UserController;
import com.example.campusactivity.dto.security.SecurityErrorResponse;
import com.example.campusactivity.service.auth.AccountAlreadyExistsException;
import com.example.campusactivity.service.auth.InvalidUserRequestException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        AuthController.class,
        UserController.class
})
public class SecurityApiExceptionHandler {
    @ExceptionHandler(AccountAlreadyExistsException.class)
    public ResponseEntity<SecurityErrorResponse> accountAlreadyExists() {
        return response(SecurityErrorCode.ACCOUNT_ALREADY_EXISTS);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            InvalidUserRequestException.class
    })
    public ResponseEntity<SecurityErrorResponse> invalidRequest() {
        return response(SecurityErrorCode.INVALID_AUTH_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<SecurityErrorResponse> internalError() {
        return response(SecurityErrorCode.INTERNAL_ERROR);
    }

    private static ResponseEntity<SecurityErrorResponse> response(
            SecurityErrorCode code
    ) {
        return ResponseEntity.status(code.status())
                .body(SecurityErrorResponse.from(code));
    }
}
