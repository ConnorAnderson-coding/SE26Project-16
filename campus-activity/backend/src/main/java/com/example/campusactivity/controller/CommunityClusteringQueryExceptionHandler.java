package com.example.campusactivity.controller;

import com.example.campusactivity.dto.security.SecurityErrorResponse;
import com.example.campusactivity.security.SecurityErrorCode;
import com.example.campusactivity.service.clustering.ClusteringQueryCode;
import com.example.campusactivity.service.clustering.ClusteringQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(
        assignableTypes = AdminCommunityClusteringController.class
)
public class CommunityClusteringQueryExceptionHandler {
    @ExceptionHandler(ClusteringQueryException.class)
    public ResponseEntity<SecurityErrorResponse> clusteringQueryError(
            ClusteringQueryException exception
    ) {
        return switch (exception.getCode()) {
            case INVALID_RUN_ID -> response(
                    HttpStatus.BAD_REQUEST,
                    ClusteringQueryCode.INVALID_RUN_ID
            );
            case RUN_NOT_FOUND -> response(
                    HttpStatus.NOT_FOUND,
                    ClusteringQueryCode.RUN_NOT_FOUND
            );
            case INVALID_CURRENT_USER_ID,
                 NO_SUCCESSFUL_RUN,
                 RESULT_NOT_AVAILABLE,
                 CORRUPT_STORED_DATA -> internalError();
        };
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<SecurityErrorResponse> unexpectedRuntimeError() {
        return internalError();
    }

    private static ResponseEntity<SecurityErrorResponse> response(
            HttpStatus status,
            ClusteringQueryCode code
    ) {
        return ResponseEntity.status(status)
                .body(new SecurityErrorResponse(
                        code.name(),
                        code.safeMessage(),
                        Map.of()
                ));
    }

    private static ResponseEntity<SecurityErrorResponse> internalError() {
        SecurityErrorCode code = SecurityErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.status())
                .body(SecurityErrorResponse.from(code));
    }
}
