package com.example.campusactivity.dto.clustering;

import com.example.campusactivity.service.clustering.ClusteringRunFailureCode;

import java.util.Objects;

public record ClusteringFailureResponse(
        ClusteringRunFailureCode code,
        String message
) {
    public ClusteringFailureResponse {
        code = Objects.requireNonNull(code, "code");
        message = code.errorMessage();
    }

    public ClusteringFailureResponse(ClusteringRunFailureCode code) {
        this(code, null);
    }
}
