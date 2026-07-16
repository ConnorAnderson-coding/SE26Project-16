package com.example.campusactivity.service.clustering;

import java.util.Objects;

public final class ClusteringResponseValidationException extends RuntimeException {
    private final ClusteringResponseValidationCode code;

    public ClusteringResponseValidationException(ClusteringResponseValidationCode code) {
        super(messageFor(code));
        this.code = code;
    }

    public ClusteringResponseValidationCode getCode() {
        return code;
    }

    private static String messageFor(ClusteringResponseValidationCode code) {
        return Objects.requireNonNull(code, "code 不能为空").message();
    }
}
