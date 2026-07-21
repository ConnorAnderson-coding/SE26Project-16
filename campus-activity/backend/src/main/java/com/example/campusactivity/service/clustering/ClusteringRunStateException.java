package com.example.campusactivity.service.clustering;

import java.util.Objects;

public final class ClusteringRunStateException extends RuntimeException {
    private final ClusteringRunStateCode code;

    public ClusteringRunStateException(ClusteringRunStateCode code) {
        super(Objects.requireNonNull(code, "code").safeMessage());
        this.code = code;
    }

    public ClusteringRunStateCode getCode() {
        return code;
    }
}
