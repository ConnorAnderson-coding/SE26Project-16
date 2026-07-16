package com.example.campusactivity.client.clustering.exception;

import java.util.Set;

final class RemoteErrorCodePolicy {
    private static final Set<String> ALLOWED_CODES = Set.of(
            "INVALID_CLUSTER_COUNT",
            "NO_EFFECTIVE_USERS",
            "UNAUTHENTICATED",
            "FORBIDDEN",
            "RUN_NOT_FOUND",
            "COMMUNITY_NOT_FOUND",
            "NO_SUCCESSFUL_RUN",
            "RUN_CONFLICT",
            "INVALID_FEATURE_SCHEMA",
            "UNSUPPORTED_FEATURE_SCHEMA",
            "INVALID_CLUSTERING_RESULT",
            "INVALID_RUN_ID",
            "INVALID_PAGE_REQUEST",
            "INVALID_SAMPLE_DATA",
            "CLUSTERING_COMPUTATION_FAILED",
            "CLUSTERING_COMPUTATION_ERROR",
            "PYTHON_SERVICE_UNAVAILABLE",
            "SERVICE_UNAVAILABLE",
            "INTERNAL_ERROR"
    );

    private RemoteErrorCodePolicy() {
    }

    static String sanitize(String errorCode) {
        if (errorCode != null && ALLOWED_CODES.contains(errorCode)) {
            return errorCode;
        }
        return ClusteringClientException.UNKNOWN_REMOTE_ERROR;
    }
}
