package com.example.campusactivity.service.clustering;

import com.example.campusactivity.client.clustering.dto.ClusteringMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class ClusteringPersistenceJsonSerializer {
    private final ObjectMapper objectMapper;

    ClusteringPersistenceJsonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String parametersJson(int clusterCount) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("algorithm", "KMEANS");
        parameters.put("clusterCount", clusterCount);
        parameters.put("randomState", 42);
        parameters.put("featureSchemaVersion", "community-features-v1");
        parameters.put("displaySchemaVersion", "community-display-v1");
        return write(parameters);
    }

    String metricsJson(ClusteringMetrics metrics) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("inertia", metrics.inertia());
        values.put("pcaExplainedVarianceRatio", metrics.pcaExplainedVarianceRatio());
        return write(values);
    }

    String topInterestsJson(List<String> topInterests) {
        return write(List.copyOf(topInterests));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new ClusteringRunStateException(
                    ClusteringRunStateCode.RESULT_SERIALIZATION_FAILED
            );
        }
    }
}
