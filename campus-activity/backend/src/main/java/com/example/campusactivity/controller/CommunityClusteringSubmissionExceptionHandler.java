package com.example.campusactivity.controller;

import com.example.campusactivity.dto.security.SecurityErrorResponse;
import com.example.campusactivity.security.SecurityErrorCode;
import com.example.campusactivity.service.clustering.ClusteringRunFailureCode;
import com.example.campusactivity.service.clustering.ClusteringServiceDisabledException;
import com.example.campusactivity.service.clustering.ClusteringSubmissionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = AdminCommunityClusteringSubmissionController.class)
public class CommunityClusteringSubmissionExceptionHandler {
    @ExceptionHandler(InvalidClusteringRequestException.class)
    public ResponseEntity<SecurityErrorResponse> invalidRequest() {
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_CLUSTERING_REQUEST",
                "聚类请求格式无效"
        );
    }

    @ExceptionHandler(ClusteringServiceDisabledException.class)
    public ResponseEntity<SecurityErrorResponse> serviceUnavailable() {
        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                "CLUSTERING_SERVICE_UNAVAILABLE",
                "聚类服务当前不可用"
        );
    }

    @ExceptionHandler(ClusteringSubmissionException.class)
    public ResponseEntity<SecurityErrorResponse> submissionFailure(
            ClusteringSubmissionException exception
    ) {
        return switch (exception.getCode()) {
            case NO_EFFECTIVE_USERS -> response(
                    HttpStatus.BAD_REQUEST,
                    ClusteringRunFailureCode.NO_EFFECTIVE_USERS.name(),
                    "没有可用于聚类的有效用户"
            );
            case INVALID_CLUSTER_COUNT -> response(
                    HttpStatus.BAD_REQUEST,
                    ClusteringRunFailureCode.INVALID_CLUSTER_COUNT.name(),
                    "聚类数量无效"
            );
            case ACTIVE_RUN_EXISTS -> response(
                    HttpStatus.CONFLICT,
                    "RUN_CONFLICT",
                    "已有聚类任务正在处理"
            );
            default -> internalError();
        };
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<SecurityErrorResponse> unexpectedFailure() {
        return internalError();
    }

    private static ResponseEntity<SecurityErrorResponse> response(
            HttpStatus status,
            String code,
            String message
    ) {
        return ResponseEntity.status(status)
                .body(new SecurityErrorResponse(code, message, Map.of()));
    }

    private static ResponseEntity<SecurityErrorResponse> internalError() {
        SecurityErrorCode code = SecurityErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.status())
                .body(SecurityErrorResponse.from(code));
    }
}
