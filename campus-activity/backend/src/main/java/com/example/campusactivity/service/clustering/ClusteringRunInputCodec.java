package com.example.campusactivity.service.clustering;

import com.example.campusactivity.client.clustering.dto.FeatureSample;
import com.example.campusactivity.entity.ClusteringRun;
import com.example.campusactivity.entity.ClusteringRunInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
final class ClusteringRunInputCodec {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Integer>> COUNT_MAP = new TypeReference<>() {
    };

    private final ObjectMapper writer;
    private final ObjectReader strictReader;

    ClusteringRunInputCodec(ObjectMapper objectMapper) {
        writer = objectMapper;
        strictReader = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .reader()
                .with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .without(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS);
    }

    ClusteringRunInput encode(ClusteringRun run, int sampleOrder, FeatureSample sample) {
        return new ClusteringRunInput(
                run,
                sample.userId(),
                sampleOrder,
                write(sample.interests()),
                sample.college(),
                sample.grade(),
                write(sample.availableTime()),
                sample.signupCount(),
                sample.approvedSignupCount(),
                sample.favoriteCount(),
                sample.checkInCount(),
                sample.feedbackCount(),
                sample.averageRating(),
                write(sample.categoryParticipationCounts())
        );
    }

    FeatureSample decode(ClusteringRunInput input) {
        try {
            return new FeatureSample(
                    input.getUserId(),
                    strictReader.forType(STRING_LIST).readValue(input.getInterestsJson()),
                    input.getCollege(),
                    input.getGrade(),
                    strictReader.forType(STRING_LIST).readValue(input.getAvailableTimeJson()),
                    input.getSignupCount(),
                    input.getApprovedSignupCount(),
                    input.getFavoriteCount(),
                    input.getCheckInCount(),
                    input.getFeedbackCount(),
                    input.getAverageRating(),
                    strictReader.forType(COUNT_MAP).readValue(
                            input.getCategoryParticipationCountsJson()
                    )
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid persisted clustering input");
        }
    }

    private String write(Object value) {
        try {
            return writer.writeValueAsString(value);
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new IllegalStateException("Unable to persist clustering input");
        }
    }
}
