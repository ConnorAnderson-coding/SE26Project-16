package com.example.campusactivity.controller;

import com.example.campusactivity.dto.clustering.TriggerClusteringRequest;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@Component
public final class TriggerClusteringRequestParser {
    private static final Set<String> ALLOWED_FIELDS = Set.of("clusterCount");
    private final ObjectReader strictReader;

    public TriggerClusteringRequestParser(ObjectMapper objectMapper) {
        strictReader = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .reader()
                .with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .without(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS);
    }

    public TriggerClusteringRequest parse(byte[] body) {
        if (body == null || new String(body, StandardCharsets.UTF_8).isBlank()) {
            return new TriggerClusteringRequest(null);
        }
        try {
            JsonNode root = strictReader.readTree(body);
            if (root == null || !root.isObject()) {
                throw invalid();
            }
            Set<String> fields = new HashSet<>();
            Iterator<String> names = root.fieldNames();
            names.forEachRemaining(fields::add);
            if (!ALLOWED_FIELDS.containsAll(fields)) {
                throw invalid();
            }

            JsonNode clusterCount = root.get("clusterCount");
            if (clusterCount == null || clusterCount.isNull()) {
                return new TriggerClusteringRequest(null);
            }
            if (!clusterCount.isIntegralNumber() || !clusterCount.canConvertToInt()) {
                throw invalid();
            }
            return new TriggerClusteringRequest(clusterCount.intValue());
        } catch (InvalidClusteringRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private static InvalidClusteringRequestException invalid() {
        return new InvalidClusteringRequestException();
    }
}
