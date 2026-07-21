package com.example.campusactivity.service.clustering;

import com.example.campusactivity.dto.clustering.ClusteringFailureResponse;
import com.example.campusactivity.dto.clustering.ClusteringMetricsResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClusteringStoredJsonParserTest {
    private ObjectMapper globalObjectMapper;
    private ClusteringStoredJsonParser parser;

    @BeforeEach
    void setUp() {
        globalObjectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        parser = new ClusteringStoredJsonParser(globalObjectMapper);
    }

    @Test
    void parsesStrictMetricsAndReturnsImmutableRatios() {
        ClusteringMetricsResponse metrics = parser.parseMetrics(
                "{\"inertia\":12.5,\"pcaExplainedVarianceRatio\":[0.7,0.2]}"
        );

        assertThat(metrics.inertia()).isEqualTo(12.5);
        assertThat(metrics.pcaExplainedVarianceRatio()).containsExactly(0.7, 0.2);
        assertThatThrownBy(() -> metrics.pcaExplainedVarianceRatio().add(0.1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"inertia\":1.0}",
            "{\"pcaExplainedVarianceRatio\":[0.5,0.4]}",
            "{\"inertia\":1.0,\"pcaExplainedVarianceRatio\":[0.5,0.4],\"extra\":1}",
            "{\"inertia\":1.0,\"inertia\":2.0,\"pcaExplainedVarianceRatio\":[0.5,0.4]}",
            "{\"inertia\":-0.1,\"pcaExplainedVarianceRatio\":[0.5,0.4]}",
            "{\"inertia\":\"1.0\",\"pcaExplainedVarianceRatio\":[0.5,0.4]}",
            "{\"inertia\":1.0,\"pcaExplainedVarianceRatio\":[0.5]}",
            "{\"inertia\":1.0,\"pcaExplainedVarianceRatio\":[0.5,0.4,0.1]}",
            "{\"inertia\":1.0,\"pcaExplainedVarianceRatio\":[-0.1,0.4]}",
            "{\"inertia\":1.0,\"pcaExplainedVarianceRatio\":[0.5,1.1]}",
            "{\"inertia\":1.0,\"pcaExplainedVarianceRatio\":[0.5,\"0.4\"]}",
            "{\"inertia\":NaN,\"pcaExplainedVarianceRatio\":[0.5,0.4]}",
            "{\"inertia\":Infinity,\"pcaExplainedVarianceRatio\":[0.5,0.4]}",
            "null",
            "[]",
            "{\"inertia\":1.0,\"pcaExplainedVarianceRatio\":[0.5,0.4]} true"
    })
    void rejectsInvalidMetricsWithOneSafeError(String json) {
        assertCorrupt(() -> parser.parseMetrics(json), json);
    }

    @Test
    void parsesTopInterestsWithoutRewritingAndPreservesOrder() {
        List<String> interests = parser.parseTopInterests(
                "[\" AI \",\"羽毛球\",\"编程\"]"
        );

        assertThat(interests).containsExactly(" AI ", "羽毛球", "编程");
        assertThatThrownBy(() -> interests.add("新增"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "null",
            "[null]",
            "[1]",
            "[\"\"]",
            "[\"   \"]",
            "[\"AI\",\"AI\"]",
            "[\"AI\",\"编程\",\"羽毛球\",\"跑步\"]",
            "[\"AI\",]",
            "[\"AI\"] false"
    })
    void rejectsInvalidTopInterestsWithOneSafeError(String json) {
        assertCorrupt(() -> parser.parseTopInterests(json), json);
    }

    @Test
    void parsesOnlyExactFixedFailureMessages() {
        for (ClusteringRunFailureCode code : ClusteringRunFailureCode.values()) {
            ClusteringFailureResponse response = parser.parseFailure(code.errorMessage());
            assertThat(response.code()).isEqualTo(code);
            assertThat(response.message()).isEqualTo(code.errorMessage());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "UNKNOWN: 未知",
            "INTERNAL_ERROR",
            "INTERNAL_ERROR: 被修改",
            "INTERNAL_ERROR: 聚类运行发生内部错误 ",
            "prefix INTERNAL_ERROR: 聚类运行发生内部错误"
    })
    void rejectsDamagedFailureMessagesWithoutLeakingThem(String storedMessage) {
        assertCorrupt(() -> parser.parseFailure(storedMessage), storedMessage);
    }

    @Test
    void usesStrictMapperCopyWithoutChangingGlobalMapper() {
        ObjectMapper strictCopy = (ObjectMapper) ReflectionTestUtils.getField(
                parser,
                "objectMapper"
        );

        assertThat(strictCopy).isNotSameAs(globalObjectMapper);
        assertThat(globalObjectMapper.isEnabled(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
        )).isFalse();
    }

    private static void assertCorrupt(ThrowingOperation operation, String secret) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(ClusteringQueryException.class, exception -> {
                    assertThat(exception.getCode())
                            .isEqualTo(ClusteringQueryCode.CORRUPT_STORED_DATA);
                    assertThat(exception.getMessage())
                            .isEqualTo(ClusteringQueryCode.CORRUPT_STORED_DATA.safeMessage());
                    if (!secret.isEmpty()) {
                        assertThat(exception.getMessage()).doesNotContain(secret);
                        assertThat(exception.toString()).doesNotContain(secret);
                    }
                    assertThat(exception.getCause()).isNull();
                });
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }
}
