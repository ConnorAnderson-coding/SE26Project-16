package com.example.campusactivity.client.clustering.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ClusteringErrorResponse(
        String code,
        String message,
        Map<String, Object> details
) {
    public ClusteringErrorResponse {
        code = Objects.requireNonNull(code, "code 不能为空");
        message = Objects.requireNonNull(message, "message 不能为空");
        Objects.requireNonNull(details, "details 不能为空");
        details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }
}
