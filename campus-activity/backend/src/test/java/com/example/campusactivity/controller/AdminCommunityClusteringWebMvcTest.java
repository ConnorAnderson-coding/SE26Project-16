package com.example.campusactivity.controller;

import com.example.campusactivity.dto.clustering.ClusteringFailureResponse;
import com.example.campusactivity.dto.clustering.ClusteringMetricsResponse;
import com.example.campusactivity.dto.clustering.ClusteringRunDetailResponse;
import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.service.clustering.ClusteringQueryCode;
import com.example.campusactivity.service.clustering.ClusteringQueryException;
import com.example.campusactivity.service.clustering.ClusteringRunFailureCode;
import com.example.campusactivity.service.clustering.CommunityClusteringQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCommunityClusteringController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminCommunityClusteringWebMvcTest {
    private static final String BASE_PATH =
            "/api/v1/admin/community-clustering/runs/{runId}";
    private static final Instant CREATED_AT =
            Instant.parse("2026-07-20T01:00:00Z");
    private static final Instant STARTED_AT =
            Instant.parse("2026-07-20T01:01:00Z");
    private static final Instant FINISHED_AT =
            Instant.parse("2026-07-20T01:02:00Z");

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private CommunityClusteringQueryService queryService;

    @Test
    void mapsInvalidRunIdToFixed400() throws Exception {
        String runId = "invalid-secret-run";
        when(queryService.findRunById(runId)).thenThrow(
                new ClusteringQueryException(ClusteringQueryCode.INVALID_RUN_ID)
        );

        mockMvc.perform(get(BASE_PATH, runId))
                .andExpect(fixedError(
                        400,
                        "INVALID_RUN_ID",
                        "聚类任务标识无效",
                        runId
                ));
    }

    @Test
    void mapsMissingRunToFixed404WithoutEchoingRunId() throws Exception {
        String runId = "missing-secret-run";
        when(queryService.findRunById(runId)).thenThrow(
                new ClusteringQueryException(ClusteringQueryCode.RUN_NOT_FOUND)
        );

        mockMvc.perform(get(BASE_PATH, runId))
                .andExpect(fixedError(
                        404,
                        "RUN_NOT_FOUND",
                        "未找到指定的聚类任务",
                        runId
                ));
    }

    @Test
    void masksCorruptStoredDataAsInternalError() throws Exception {
        String runId = "corrupt-secret-run";
        when(queryService.findRunById(runId)).thenThrow(
                new ClusteringQueryException(
                        ClusteringQueryCode.CORRUPT_STORED_DATA
                )
        );

        mockMvc.perform(get(BASE_PATH, runId))
                .andExpect(fixedError(
                        500,
                        "INTERNAL_ERROR",
                        "服务器内部错误",
                        runId,
                        "CORRUPT_STORED_DATA",
                        "聚类存储数据已损坏"
                ));
    }

    @ParameterizedTest
    @EnumSource(
            value = ClusteringQueryCode.class,
            names = {
                    "INVALID_CURRENT_USER_ID",
                    "NO_SUCCESSFUL_RUN",
                    "RESULT_NOT_AVAILABLE"
            }
    )
    void masksQueryCodesThatAreUnreachableFromThisEndpoint(
            ClusteringQueryCode code
    ) throws Exception {
        String runId = "unreachable-secret-run";
        when(queryService.findRunById(runId)).thenThrow(
                new ClusteringQueryException(code)
        );

        mockMvc.perform(get(BASE_PATH, runId))
                .andExpect(fixedError(
                        500,
                        "INTERNAL_ERROR",
                        "服务器内部错误",
                        runId,
                        code.name(),
                        code.safeMessage()
                ));
    }

    @Test
    void masksUnexpectedRuntimeExceptionAndDynamicDetails() throws Exception {
        String runId = "runtime-secret-run";
        String dynamicMessage = """
                SELECT password FROM users;
                {"private":"json"}
                java.lang.IllegalStateException stackTrace
                """;
        when(queryService.findRunById(runId))
                .thenThrow(new IllegalStateException(dynamicMessage));

        mockMvc.perform(get(BASE_PATH, runId))
                .andExpect(fixedError(
                        500,
                        "INTERNAL_ERROR",
                        "服务器内部错误",
                        runId,
                        "SELECT",
                        "{\"private\":\"json\"}",
                        "IllegalStateException",
                        "stackTrace",
                        dynamicMessage
                ));
    }

    @Test
    void serializesPendingNullSemanticsWithoutInternalFields() throws Exception {
        String runId = "pending-run";
        when(queryService.findRunById(runId)).thenReturn(new ClusteringRunDetailResponse(
                runId,
                "pending-version",
                ClusteringAlgorithm.KMEANS,
                2,
                42,
                ClusteringRunStatus.PENDING,
                null,
                "community-features-v1",
                null,
                null,
                CREATED_AT,
                null,
                null,
                "admin-pending"
        ));

        mockMvc.perform(get(BASE_PATH, runId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.metrics").value((Object) null))
                .andExpect(jsonPath("$.failure").value((Object) null))
                .andExpect(jsonPath("$.startedAt").value((Object) null))
                .andExpect(jsonPath("$.finishedAt").value((Object) null))
                .andExpect(jsonPath("$.createdBy").value("admin-pending"))
                .andExpect(noInternalFields());
    }

    @Test
    void serializesRunningNullSemantics() throws Exception {
        String runId = "running-run";
        when(queryService.findRunById(runId)).thenReturn(new ClusteringRunDetailResponse(
                runId,
                "running-version",
                ClusteringAlgorithm.KMEANS,
                2,
                42,
                ClusteringRunStatus.RUNNING,
                2,
                "community-features-v1",
                null,
                null,
                CREATED_AT,
                STARTED_AT,
                null,
                "admin-running"
        ));

        mockMvc.perform(get(BASE_PATH, runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.metrics").value((Object) null))
                .andExpect(jsonPath("$.failure").value((Object) null))
                .andExpect(jsonPath("$.startedAt").isString())
                .andExpect(jsonPath("$.finishedAt").value((Object) null))
                .andExpect(noInternalFields());
    }

    @Test
    void serializesStructuredSuccessMetricsAndPcaArray() throws Exception {
        String runId = "success-run";
        when(queryService.findRunById(runId)).thenReturn(new ClusteringRunDetailResponse(
                runId,
                "success-version",
                ClusteringAlgorithm.KMEANS,
                2,
                42,
                ClusteringRunStatus.SUCCESS,
                18,
                "community-features-v1",
                new ClusteringMetricsResponse(31.48, List.of(0.34, 0.21)),
                null,
                CREATED_AT,
                STARTED_AT,
                FINISHED_AT,
                "admin-success"
        ));

        mockMvc.perform(get(BASE_PATH, runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.sampleCount").value(18))
                .andExpect(jsonPath("$.metrics.inertia").value(31.48))
                .andExpect(jsonPath(
                        "$.metrics.pcaExplainedVarianceRatio[0]"
                ).value(0.34))
                .andExpect(jsonPath(
                        "$.metrics.pcaExplainedVarianceRatio[1]"
                ).value(0.21))
                .andExpect(jsonPath(
                        "$.metrics.pcaExplainedVarianceRatio.length()"
                ).value(2))
                .andExpect(jsonPath("$.failure").value((Object) null))
                .andExpect(jsonPath("$.createdBy").value("admin-success"))
                .andExpect(noInternalFields());
    }

    @Test
    void serializesStructuredFixedFailure() throws Exception {
        String runId = "failed-run";
        when(queryService.findRunById(runId)).thenReturn(new ClusteringRunDetailResponse(
                runId,
                "failed-version",
                ClusteringAlgorithm.KMEANS,
                2,
                42,
                ClusteringRunStatus.FAILED,
                null,
                "community-features-v1",
                null,
                new ClusteringFailureResponse(
                        ClusteringRunFailureCode.INTERNAL_ERROR
                ),
                CREATED_AT,
                null,
                FINISHED_AT,
                "admin-failed"
        ));

        mockMvc.perform(get(BASE_PATH, runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.metrics").value((Object) null))
                .andExpect(jsonPath("$.failure.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.failure.message").value(
                        "INTERNAL_ERROR: 聚类运行发生内部错误"
                ))
                .andExpect(jsonPath("$.startedAt").value((Object) null))
                .andExpect(jsonPath("$.finishedAt").isString())
                .andExpect(noInternalFields());
    }

    private static ResultMatcher fixedError(
            int expectedStatus,
            String expectedCode,
            String expectedMessage,
            String... forbiddenText
    ) {
        return result -> {
            assertThat(result.getResponse().getStatus())
                    .isEqualTo(expectedStatus);
            assertThat(MediaType.parseMediaType(
                    result.getResponse().getContentType()
            ).isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
            String body = result.getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
            JsonNode json = new ObjectMapper().readTree(body);
            assertThat(json.fieldNames())
                    .toIterable()
                    .containsExactlyInAnyOrder("code", "message", "details");
            assertThat(json.get("code").asText()).isEqualTo(expectedCode);
            assertThat(json.get("message").asText()).isEqualTo(expectedMessage);
            assertThat(json.get("details").isObject()).isTrue();
            assertThat(json.get("details").isEmpty()).isTrue();
            assertThat(body).doesNotContain(forbiddenText);
        };
    }

    private static ResultMatcher noInternalFields() {
        return result -> {
            String body = result.getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
            JsonNode json = new ObjectMapper().readTree(body);
            assertThat(json.has("errorMessage")).isFalse();
            assertThat(json.has("metricsJson")).isFalse();
            assertThat(json.has("parametersJson")).isFalse();
            assertThat(json.has("activeSlot")).isFalse();
            assertThat(body)
                    .doesNotContain(
                            "password",
                            "userId",
                            "UserAccount",
                            "JSESSIONID",
                            "CSRF"
                    );
        };
    }
}
