package com.example.campusactivity.controller;

import com.example.campusactivity.dto.clustering.ClusteringRunSummaryResponse;
import com.example.campusactivity.dto.clustering.CommunityMemberPointResponse;
import com.example.campusactivity.dto.clustering.CommunityResponse;
import com.example.campusactivity.dto.clustering.CurrentUserClusteringResponse;
import com.example.campusactivity.dto.clustering.CurrentUserMembershipResponse;
import com.example.campusactivity.dto.clustering.LatestClusteringResponse;
import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.service.clustering.ClusteringQueryCode;
import com.example.campusactivity.service.clustering.ClusteringQueryException;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        CommunityClusteringController.class,
        AdminCommunityClusteringController.class
})
@AutoConfigureMockMvc(addFilters = false)
class CommunityClusteringWebMvcTest {
    private static final String CURRENT_USER_ID = "private-current-user";
    private static final String LATEST_PATH =
            "/api/v1/community-clustering/latest";
    private static final String ME_PATH = "/api/v1/community-clustering/me";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private CommunityClusteringQueryService queryService;

    @Test
    void mapsNoSuccessfulRunToFixed404() throws Exception {
        when(queryService.findLatestClustering(CURRENT_USER_ID)).thenThrow(
                new ClusteringQueryException(
                        ClusteringQueryCode.NO_SUCCESSFUL_RUN
                )
        );

        mockMvc.perform(get(LATEST_PATH).principal(authentication()))
                .andExpect(fixedError(
                        404,
                        "NO_SUCCESSFUL_RUN",
                        "当前还没有可用的社区聚类结果",
                        CURRENT_USER_ID
                ));
    }

    @ParameterizedTest
    @EnumSource(
            value = ClusteringQueryCode.class,
            names = {
                    "INVALID_CURRENT_USER_ID",
                    "CORRUPT_STORED_DATA",
                    "RESULT_NOT_AVAILABLE",
                    "RUN_NOT_FOUND",
                    "INVALID_RUN_ID"
            }
    )
    void masksAllOtherQueryCodesAsInternalError(ClusteringQueryCode code)
            throws Exception {
        when(queryService.findLatestClustering(CURRENT_USER_ID)).thenThrow(
                new ClusteringQueryException(code)
        );

        mockMvc.perform(get(LATEST_PATH).principal(authentication()))
                .andExpect(fixedError(
                        500,
                        "INTERNAL_ERROR",
                        "服务器内部错误",
                        CURRENT_USER_ID,
                        code.name(),
                        code.safeMessage()
                ));
    }

    @Test
    void masksUnexpectedRuntimeExceptionAndDynamicDetails() throws Exception {
        String dynamicMessage = """
                SELECT password FROM users;
                {"private":"json"}
                java.lang.IllegalStateException stackTrace
                """;
        when(queryService.findCurrentUserClustering(CURRENT_USER_ID))
                .thenThrow(new IllegalStateException(dynamicMessage));

        mockMvc.perform(get(ME_PATH).principal(authentication()))
                .andExpect(fixedError(
                        500,
                        "INTERNAL_ERROR",
                        "服务器内部错误",
                        CURRENT_USER_ID,
                        "SELECT",
                        "{\"private\":\"json\"}",
                        "IllegalStateException",
                        "stackTrace",
                        dynamicMessage
                ));
    }

    @Test
    void masksAuthenticationNameFailureAsInternalError() throws Exception {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName())
                .thenThrow(new IllegalStateException("principal-private-failure"))
                .thenReturn("framework-audit-user");

        mockMvc.perform(get(LATEST_PATH).principal(authentication))
                .andExpect(fixedError(
                        500,
                        "INTERNAL_ERROR",
                        "服务器内部错误",
                        "principal-private-failure",
                        "IllegalStateException"
                ));
    }

    @Test
    void serializesLatestWithOnlyApprovedPublicFields() throws Exception {
        when(queryService.findLatestClustering(CURRENT_USER_ID))
                .thenReturn(latestResponse());

        mockMvc.perform(get(LATEST_PATH).principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.run.runId").value("run-latest"))
                .andExpect(jsonPath("$.communities").isArray())
                .andExpect(jsonPath("$.communities[0].points").isArray())
                .andExpect(jsonPath(
                        "$.communities[0].points[0].currentUser"
                ).isBoolean())
                .andExpect(jsonPath(
                        "$.communities[0].points[0].isCurrentUser"
                ).doesNotExist())
                .andExpect(latestPrivacy());
    }

    @Test
    void serializesCurrentUserMembershipWithOnlyApprovedFields()
            throws Exception {
        when(queryService.findCurrentUserClustering(CURRENT_USER_ID))
                .thenReturn(currentUserResponse());

        mockMvc.perform(get(ME_PATH).principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.membership.communityId")
                        .value("community-1"))
                .andExpect(jsonPath("$.membership.distanceToCenter")
                        .isNumber())
                .andExpect(currentUserPrivacy());
    }

    @Test
    void serializesNullMembershipAsJsonNull() throws Exception {
        when(queryService.findCurrentUserClustering(CURRENT_USER_ID))
                .thenReturn(new CurrentUserClusteringResponse(
                        "run-latest",
                        "version-latest",
                        null
                ));

        mockMvc.perform(get(ME_PATH).principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("run-latest"))
                .andExpect(jsonPath("$.membership").value((Object) null));
    }

    @Test
    void existingAdminAdviceMappingsRemainUnchanged() throws Exception {
        when(queryService.findRunById("invalid-run")).thenThrow(
                new ClusteringQueryException(ClusteringQueryCode.INVALID_RUN_ID)
        );
        when(queryService.findRunById("missing-run")).thenThrow(
                new ClusteringQueryException(ClusteringQueryCode.RUN_NOT_FOUND)
        );

        mockMvc.perform(get(
                        "/api/v1/admin/community-clustering/runs/{runId}",
                        "invalid-run"
                ))
                .andExpect(fixedError(
                        400,
                        "INVALID_RUN_ID",
                        "聚类任务标识无效",
                        "invalid-run"
                ));
        mockMvc.perform(get(
                        "/api/v1/admin/community-clustering/runs/{runId}",
                        "missing-run"
                ))
                .andExpect(fixedError(
                        404,
                        "RUN_NOT_FOUND",
                        "未找到指定的聚类任务",
                        "missing-run"
                ));
    }

    private ResultMatcher fixedError(
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
            JsonNode json = objectMapper.readTree(body);
            assertThat(fieldNames(json))
                    .containsExactlyInAnyOrder("code", "message", "details");
            assertThat(json.get("code").asText()).isEqualTo(expectedCode);
            assertThat(json.get("message").asText()).isEqualTo(expectedMessage);
            assertThat(json.get("details").isObject()).isTrue();
            assertThat(json.get("details").isEmpty()).isTrue();
            assertThat(body).doesNotContain(forbiddenText);
        };
    }

    private ResultMatcher latestPrivacy() {
        return result -> {
            JsonNode json = objectMapper.readTree(
                    result.getResponse().getContentAsString(
                            StandardCharsets.UTF_8
                    )
            );
            assertThat(fieldNames(json))
                    .containsExactlyInAnyOrder("run", "communities");
            assertThat(fieldNames(json.get("run")))
                    .containsExactlyInAnyOrder(
                            "runId",
                            "version",
                            "algorithm",
                            "clusterCount",
                            "sampleCount",
                            "finishedAt"
                    );
            JsonNode community = json.get("communities").get(0);
            assertThat(fieldNames(community))
                    .containsExactlyInAnyOrder(
                            "communityId",
                            "clusterNo",
                            "name",
                            "description",
                            "memberCount",
                            "topInterests",
                            "color",
                            "points"
                    );
            assertThat(fieldNames(community.get("points").get(0)))
                    .containsExactlyInAnyOrder(
                            "pointId",
                            "x",
                            "y",
                            "currentUser"
                    );
            assertThat(json.toString()).doesNotContain(
                    "userId",
                    "distanceToCenter",
                    "assignedAt",
                    "createdBy",
                    "metrics",
                    "failure",
                    "activeSlot",
                    "parametersJson",
                    "metricsJson",
                    "errorMessage",
                    "JSESSIONID",
                    "CSRF",
                    "authorities",
                    CURRENT_USER_ID
            );
        };
    }

    private ResultMatcher currentUserPrivacy() {
        return result -> {
            JsonNode json = objectMapper.readTree(
                    result.getResponse().getContentAsString(
                            StandardCharsets.UTF_8
                    )
            );
            assertThat(fieldNames(json))
                    .containsExactlyInAnyOrder("runId", "version", "membership");
            assertThat(fieldNames(json.get("membership")))
                    .containsExactlyInAnyOrder(
                            "communityId",
                            "clusterNo",
                            "communityName",
                            "color",
                            "pointId",
                            "x",
                            "y",
                            "distanceToCenter"
                    );
            assertThat(json.toString()).doesNotContain(
                    "userId",
                    "college",
                    "grade",
                    "interests",
                    "role",
                    "password",
                    "UserAccount",
                    "JSESSIONID",
                    "CSRF",
                    CURRENT_USER_ID
            );
        };
    }

    private static Set<String> fieldNames(JsonNode node) {
        java.util.HashSet<String> names = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                CURRENT_USER_ID,
                "unused",
                List.of()
        );
    }

    private static LatestClusteringResponse latestResponse() {
        return new LatestClusteringResponse(
                new ClusteringRunSummaryResponse(
                        "run-latest",
                        "version-latest",
                        ClusteringAlgorithm.KMEANS,
                        2,
                        2,
                        Instant.parse("2026-07-21T02:00:00Z")
                ),
                List.of(new CommunityResponse(
                        "community-1",
                        0,
                        "社区 1",
                        "主要兴趣：AI",
                        2,
                        List.of("AI"),
                        "#1677FF",
                        List.of(
                                new CommunityMemberPointResponse(
                                        "point-a",
                                        12.5,
                                        34.5,
                                        true
                                ),
                                new CommunityMemberPointResponse(
                                        "point-b",
                                        56.5,
                                        78.5,
                                        false
                                )
                        )
                ))
        );
    }

    private static CurrentUserClusteringResponse currentUserResponse() {
        return new CurrentUserClusteringResponse(
                "run-latest",
                "version-latest",
                new CurrentUserMembershipResponse(
                        "community-1",
                        0,
                        "社区 1",
                        "#1677FF",
                        "point-a",
                        12.5,
                        34.5,
                        0.75
                )
        );
    }
}
