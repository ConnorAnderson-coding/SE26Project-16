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
        assignableTypes = CommunityClusteringController.class
)
public class CommunityClusteringUserQueryExceptionHandler {
    @ExceptionHandler(ClusteringQueryException.class)
    public ResponseEntity<SecurityErrorResponse> clusteringQueryError(
            ClusteringQueryException exception
    ) {
        if (exception.getCode() == ClusteringQueryCode.NO_SUCCESSFUL_RUN) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new SecurityErrorResponse(
                            ClusteringQueryCode.NO_SUCCESSFUL_RUN.name(),
                            ClusteringQueryCode.NO_SUCCESSFUL_RUN.safeMessage(),
                            Map.of()
                    ));
        }
        return internalError();
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<SecurityErrorResponse> unexpectedRuntimeError() {
        return internalError();
    }

    private static ResponseEntity<SecurityErrorResponse> internalError() {
        SecurityErrorCode code = SecurityErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.status())
                .body(SecurityErrorResponse.from(code));
    }
}
