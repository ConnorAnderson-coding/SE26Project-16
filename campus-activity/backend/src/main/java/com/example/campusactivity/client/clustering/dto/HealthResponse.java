package com.example.campusactivity.client.clustering.dto;

import java.util.List;

public record HealthResponse(
        String status,
        String service,
        List<String> supportedFeatureSchemas
) {
    public HealthResponse {
        if (!"UP".equals(ClusteringContractChecks.string(status, "status"))) {
            throw new IllegalArgumentException("status 必须为 UP");
        }
        if (!"clustering-service".equals(ClusteringContractChecks.string(service, "service"))) {
            throw new IllegalArgumentException("service 必须为 clustering-service");
        }
        supportedFeatureSchemas = ClusteringContractChecks.stringList(
                supportedFeatureSchemas,
                "supportedFeatureSchemas",
                false
        );
    }
}
