package com.example.campusactivity.service.clustering;

import java.util.Objects;

public final class ClusteringQueryException extends RuntimeException {
    private final ClusteringQueryCode code;

    public ClusteringQueryException(ClusteringQueryCode code) {
        super(Objects.requireNonNull(code, "code").safeMessage());
        this.code = code;
    }

    public ClusteringQueryCode getCode() {
        return code;
    }
}
