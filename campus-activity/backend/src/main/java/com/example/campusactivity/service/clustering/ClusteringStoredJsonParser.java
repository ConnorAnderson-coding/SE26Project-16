package com.example.campusactivity.service.clustering;

import com.example.campusactivity.dto.clustering.ClusteringFailureResponse;
import com.example.campusactivity.dto.clustering.ClusteringMetricsResponse;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Component
public final class ClusteringStoredJsonParser {
    private static final Set<String> METRICS_FIELDS = Set.of(
            "inertia",
            "pcaExplainedVarianceRatio"
    );

    private final ObjectMapper objectMapper;
    private final ObjectReader objectReader;

    public ClusteringStoredJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.objectReader = this.objectMapper.reader()
                .with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .without(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS);
    }

    public ClusteringMetricsResponse parseMetrics(String metricsJson) {
        try {
            JsonNode root = readRequiredTree(metricsJson);
            if (!root.isObject() || !hasExactlyMetricsFields(root)) {
                throw corrupt();
            }

            JsonNode inertiaNode = root.get("inertia");
            JsonNode pcaNode = root.get("pcaExplainedVarianceRatio");
            double inertia = finiteNumber(inertiaNode);
            if (inertia < 0.0 || !pcaNode.isArray() || pcaNode.size() != 2) {
                throw corrupt();
            }

            double first = ratio(pcaNode.get(0));
            double second = ratio(pcaNode.get(1));
            return new ClusteringMetricsResponse(inertia, List.of(first, second));
        } catch (ClusteringQueryException exception) {
            throw exception;
        } catch (Exception exception) {
            throw corrupt();
        }
    }

    public List<String> parseTopInterests(String topInterestsJson) {
        try {
            JsonNode root = readRequiredTree(topInterestsJson);
            if (!root.isArray() || root.size() > 3) {
                throw corrupt();
            }

            Set<String> unique = new HashSet<>();
            java.util.ArrayList<String> interests = new java.util.ArrayList<>(root.size());
            for (JsonNode item : root) {
                if (!item.isTextual()) {
                    throw corrupt();
                }
                String interest = item.textValue();
                if (interest.isBlank() || !unique.add(interest)) {
                    throw corrupt();
                }
                interests.add(interest);
            }
            return List.copyOf(interests);
        } catch (ClusteringQueryException exception) {
            throw exception;
        } catch (Exception exception) {
            throw corrupt();
        }
    }

    public ClusteringFailureResponse parseFailure(String errorMessage) {
        try {
            if (errorMessage == null) {
                throw corrupt();
            }
            int separator = errorMessage.indexOf(": ");
            if (separator <= 0) {
                throw corrupt();
            }
            ClusteringRunFailureCode code = ClusteringRunFailureCode.valueOf(
                    errorMessage.substring(0, separator)
            );
            if (!errorMessage.equals(code.errorMessage())) {
                throw corrupt();
            }
            return new ClusteringFailureResponse(code);
        } catch (ClusteringQueryException exception) {
            throw exception;
        } catch (Exception exception) {
            throw corrupt();
        }
    }

    private JsonNode readRequiredTree(String json) throws Exception {
        if (json == null) {
            throw corrupt();
        }
        JsonNode root = objectReader.readTree(json);
        if (root == null || root.isNull()) {
            throw corrupt();
        }
        return root;
    }

    private boolean hasExactlyMetricsFields(JsonNode root) {
        Set<String> fields = new HashSet<>();
        Iterator<String> names = root.fieldNames();
        names.forEachRemaining(fields::add);
        return fields.equals(METRICS_FIELDS);
    }

    private double ratio(JsonNode node) {
        double value = finiteNumber(node);
        if (value < 0.0 || value > 1.0) {
            throw corrupt();
        }
        return value;
    }

    private double finiteNumber(JsonNode node) {
        if (node == null || !node.isNumber()) {
            throw corrupt();
        }
        double value = node.doubleValue();
        if (!Double.isFinite(value)) {
            throw corrupt();
        }
        return value;
    }

    private ClusteringQueryException corrupt() {
        return new ClusteringQueryException(ClusteringQueryCode.CORRUPT_STORED_DATA);
    }
}
