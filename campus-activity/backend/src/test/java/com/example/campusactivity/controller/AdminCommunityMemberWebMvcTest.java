package com.example.campusactivity.controller;

import com.example.campusactivity.dto.clustering.AdminCommunityMemberResponse;
import com.example.campusactivity.dto.clustering.AdminCommunitySummaryResponse;
import com.example.campusactivity.dto.clustering.CommunityMembersPageResponse;
import com.example.campusactivity.service.clustering.ClusteringQueryCode;
import com.example.campusactivity.service.clustering.ClusteringQueryException;
import com.example.campusactivity.service.clustering.CommunityClusteringQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCommunityMemberController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminCommunityMemberWebMvcTest {
    private static final String PATH =
            "/api/v1/admin/community-clustering/communities/{communityId}/members";

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private CommunityClusteringQueryService queryService;

    @Test
    void serializesOnlyApprovedAdminIdentityAndPointFields() throws Exception {
        when(queryService.findCommunityMembers("community-1", "0", "20"))
                .thenReturn(response());

        mockMvc.perform(get(PATH, "community-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.community.communityId").value("community-1"))
                .andExpect(jsonPath("$.community.runId").value("run-1"))
                .andExpect(jsonPath("$.items[0].userId").value("user-1"))
                .andExpect(jsonPath("$.items[0].name").value("张同学"))
                .andExpect(jsonPath("$.items[0].college").value("软件学院"))
                .andExpect(jsonPath("$.items[0].grade").value("2026"))
                .andExpect(jsonPath("$.items[0].pointId").value("point-1"))
                .andExpect(jsonPath("$.items[0].x").value(12.3))
                .andExpect(jsonPath("$.items[0].y").value(45.6))
                .andExpect(jsonPath("$.items[0].distanceToCenter").value(0.48))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(37))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(noPrivateFields());
    }

    @Test
    void forwardsCustomPaginationAndIgnoresClientSort() throws Exception {
        when(queryService.findCommunityMembers("community-1", "2", "7"))
                .thenReturn(new CommunityMembersPageResponse(
                        response().community(), List.of(), 2, 7, 37, 6
                ));

        mockMvc.perform(get(PATH, "community-1")
                        .queryParam("page", "2")
                        .queryParam("size", "7")
                        .queryParam("sort", "user.password,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(7));
    }

    @Test
    void mapsInvalidPageAndCommunityIdToFixed400() throws Exception {
        when(queryService.findCommunityMembers("community-1", "bad-secret", "20"))
                .thenThrow(new ClusteringQueryException(
                        ClusteringQueryCode.INVALID_PAGE_REQUEST
                ));
        mockMvc.perform(get(PATH, "community-1").queryParam("page", "bad-secret"))
                .andExpect(fixedError(
                        400, "INVALID_PAGE_REQUEST", "分页请求无效", "bad-secret"
                ));

        when(queryService.findCommunityMembers("bad-id", "0", "20"))
                .thenThrow(new ClusteringQueryException(
                        ClusteringQueryCode.INVALID_COMMUNITY_ID
                ));
        mockMvc.perform(get(PATH, "bad-id"))
                .andExpect(fixedError(
                        400, "INVALID_COMMUNITY_ID", "社区标识无效", "bad-id"
                ));
    }

    @Test
    void mapsMissingCommunityToFixed404AndCorruptionToInternal500()
            throws Exception {
        when(queryService.findCommunityMembers("missing", "0", "20"))
                .thenThrow(new ClusteringQueryException(
                        ClusteringQueryCode.COMMUNITY_NOT_FOUND
                ));
        mockMvc.perform(get(PATH, "missing"))
                .andExpect(fixedError(
                        404, "COMMUNITY_NOT_FOUND", "未找到指定的社区", "missing"
                ));

        when(queryService.findCommunityMembers("corrupt", "0", "20"))
                .thenThrow(new ClusteringQueryException(
                        ClusteringQueryCode.CORRUPT_STORED_DATA
                ));
        mockMvc.perform(get(PATH, "corrupt"))
                .andExpect(fixedError(
                        500, "INTERNAL_ERROR", "服务器内部错误",
                        "corrupt", "CORRUPT_STORED_DATA"
                ));
    }

    private static CommunityMembersPageResponse response() {
        return new CommunityMembersPageResponse(
                new AdminCommunitySummaryResponse(
                        "community-1", "run-1", 0, "社区 1", "#1677FF", 37
                ),
                List.of(new AdminCommunityMemberResponse(
                        "user-1", "张同学", "软件学院", "2026",
                        "point-1", 12.3, 45.6, 0.48
                )),
                0, 20, 37, 2
        );
    }

    private static ResultMatcher noPrivateFields() {
        return result -> assertThat(result.getResponse().getContentAsString(
                StandardCharsets.UTF_8
        )).doesNotContain(
                "password", "passwordHash", "role", "authorities", "email",
                "phone", "friends", "interests", "assignedAt",
                "parametersJson", "metricsJson", "errorMessage"
        );
    }

    private static ResultMatcher fixedError(
            int status,
            String code,
            String message,
            String... forbidden
    ) {
        return result -> {
            assertThat(result.getResponse().getStatus()).isEqualTo(status);
            assertThat(MediaType.parseMediaType(result.getResponse().getContentType())
                    .isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
            String body = result.getResponse().getContentAsString(
                    StandardCharsets.UTF_8
            );
            JsonNode json = new ObjectMapper().readTree(body);
            assertThat(json.fieldNames()).toIterable()
                    .containsExactlyInAnyOrder("code", "message", "details");
            assertThat(json.get("code").asText()).isEqualTo(code);
            assertThat(json.get("message").asText()).isEqualTo(message);
            assertThat(json.get("details").isEmpty()).isTrue();
            assertThat(body).doesNotContain(forbidden);
        };
    }
}
