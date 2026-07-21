package com.example.campusactivity.controller;

import com.example.campusactivity.client.clustering.ClusteringClient;
import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRun;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.entity.Community;
import com.example.campusactivity.entity.CommunityMember;
import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.ClusteringRunRepository;
import com.example.campusactivity.repository.CommunityMemberRepository;
import com.example.campusactivity.repository.CommunityRepository;
import com.example.campusactivity.repository.UserRepository;
import com.example.campusactivity.service.clustering.CommunityClusteringOrchestrator;
import com.example.campusactivity.service.clustering.CommunityClusteringDispatcher;
import com.example.campusactivity.service.clustering.CommunityClusteringRunExecutor;
import com.example.campusactivity.service.clustering.CommunityClusteringStartupRecovery;
import com.example.campusactivity.service.clustering.CommunityClusteringQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "campus.demo-password=TestPassword123!",
        "community-clustering.python.enabled=false"
})
@AutoConfigureMockMvc
class CommunityClusteringDisabledIntegrationTest {
    private static final String PASSWORD = "StrongPassword123!";
    private static final String CURRENT_USER_ID = "disabled-query-current";
    private static final String ABSENT_USER_ID = "disabled-query-absent";
    private static final String FIRST_USER_ID = "disabled-query-first";
    private static final String SECOND_USER_ID = "disabled-query-second";
    private static final String RUN_ID = "disabled-query-success-run";
    private static final String VERSION = "disabled-query-version";
    private static final Instant CREATED_AT =
            Instant.parse("2099-07-21T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private Environment environment;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ClusteringRunRepository runRepository;
    @Autowired
    private CommunityRepository communityRepository;
    @Autowired
    private CommunityMemberRepository memberRepository;
    @Autowired
    private CommunityClusteringController controller;
    @Autowired
    private CommunityClusteringQueryService queryService;

    @BeforeEach
    void createHistoricalSuccess() {
        cleanClusteringData();
        deleteUsers();
        UserAccount currentUser = saveUser(CURRENT_USER_ID);
        UserAccount absentUser = saveUser(ABSENT_USER_ID);
        UserAccount firstUser = saveUser(FIRST_USER_ID);
        UserAccount secondUser = saveUser(SECOND_USER_ID);
        assertThat(absentUser).isNotNull();

        ClusteringRun run = runRepository.saveAndFlush(successfulRun());
        Community second = communityRepository.saveAndFlush(
                community("disabled-community-1", run, 1, 1)
        );
        Community first = communityRepository.saveAndFlush(
                community("disabled-community-0", run, 0, 2)
        );
        memberRepository.saveAllAndFlush(List.of(
                member(
                        "point-z",
                        run,
                        first,
                        secondUser,
                        70.0,
                        80.0,
                        0.8
                ),
                member(
                        "point-current",
                        run,
                        second,
                        currentUser,
                        90.0,
                        10.0,
                        0.3
                ),
                member(
                        "point-a",
                        run,
                        first,
                        firstUser,
                        20.0,
                        30.0,
                        0.5
                )
        ));
    }

    @AfterEach
    void cleanFixtures() {
        cleanClusteringData();
        deleteUsers();
    }

    @Test
    void disabledPythonKeepsHistoricalLatestAndMembershipQueriesAvailable()
            throws Exception {
        assertDisabledBeans();
        MockHttpSession currentSession = loggedIn(CURRENT_USER_ID);

        MvcResult latestResult = mockMvc.perform(get(
                        "/api/v1/community-clustering/latest"
                ).session(currentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.runId").value(RUN_ID))
                .andExpect(jsonPath("$.run.version").value(VERSION))
                .andExpect(jsonPath("$.communities[0].clusterNo").value(0))
                .andExpect(jsonPath("$.communities[1].clusterNo").value(1))
                .andReturn();
        JsonNode latest = objectMapper.readTree(
                latestResult.getResponse().getContentAsString(
                        StandardCharsets.UTF_8
                )
        );
        assertThat(latest.at("/communities/0/points/0/pointId").asText())
                .isEqualTo("point-a");
        assertThat(latest.at("/communities/0/points/1/pointId").asText())
                .isEqualTo("point-z");
        assertThat(latest.at("/communities/1/points/0/pointId").asText())
                .isEqualTo("point-current");
        assertThat(latest.at("/communities/1/points/0/currentUser").asBoolean())
                .isTrue();

        mockMvc.perform(get("/api/v1/community-clustering/me")
                        .session(currentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(RUN_ID))
                .andExpect(jsonPath("$.membership.communityId")
                        .value("disabled-community-1"))
                .andExpect(jsonPath("$.membership.pointId")
                        .value("point-current"))
                .andExpect(jsonPath("$.membership.distanceToCenter")
                        .value(0.3));

        MockHttpSession absentSession = loggedIn(ABSENT_USER_ID);
        mockMvc.perform(get("/api/v1/community-clustering/me")
                        .session(absentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(RUN_ID))
                .andExpect(jsonPath("$.membership").value((Object) null));
    }

    @Test
    void disabledPythonReturnsNoSuccessfulRun404InsteadOf503()
            throws Exception {
        assertDisabledBeans();
        MockHttpSession session = loggedIn(CURRENT_USER_ID);
        cleanClusteringData();

        mockMvc.perform(get("/api/v1/community-clustering/latest")
                        .session(session))
                .andExpect(fixedNoSuccessfulRun());
        mockMvc.perform(get("/api/v1/community-clustering/me")
                        .session(session))
                .andExpect(fixedNoSuccessfulRun());
    }

    private void assertDisabledBeans() {
        assertThat(environment.getProperty(
                "community-clustering.python.enabled",
                Boolean.class
        )).isFalse();
        assertThat(controller).isNotNull();
        assertThat(queryService).isNotNull();
        assertThat(applicationContext.getBeansOfType(
                CommunityClusteringOrchestrator.class
        )).isEmpty();
        assertThat(applicationContext.getBeansOfType(
                CommunityClusteringDispatcher.class
        )).isEmpty();
        assertThat(applicationContext.getBeansOfType(
                CommunityClusteringRunExecutor.class
        )).isEmpty();
        assertThat(applicationContext.getBeansOfType(
                CommunityClusteringStartupRecovery.class
        )).isEmpty();
        assertThat(applicationContext.getBeansOfType(
                ClusteringClient.class
        )).isEmpty();
    }

    private MockHttpSession loggedIn(String id) throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session =
                (MockHttpSession) csrfResult.getRequest().getSession(false);
        JsonNode csrf = objectMapper.readTree(
                csrfResult.getResponse().getContentAsString(
                        StandardCharsets.UTF_8
                )
        );
        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header(
                                csrf.get("headerName").asText(),
                                csrf.get("token").asText()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", id,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk());
        return session;
    }

    private UserAccount saveUser(String id) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setName("Disabled Query User");
        user.setRole("student");
        user.setCollege("Software");
        user.setGrade("2026");
        user.setInterests(new ArrayList<>(List.of("AI")));
        user.setAvailableTime(new ArrayList<>(List.of("weekend")));
        user.setFriends(new ArrayList<>());
        return userRepository.saveAndFlush(user);
    }

    private static ClusteringRun successfulRun() {
        ClusteringRun run = new ClusteringRun();
        run.setId(RUN_ID);
        run.setVersion(VERSION);
        run.setAlgorithm(ClusteringAlgorithm.KMEANS);
        run.setClusterCount(2);
        run.setRandomState(42);
        run.setStatus(ClusteringRunStatus.SUCCESS);
        run.setActiveSlot(null);
        run.setSampleCount(3);
        run.setFeatureSchemaVersion("community-features-v1");
        run.setParametersJson("{}");
        run.setMetricsJson(
                "{\"inertia\":2.5,\"pcaExplainedVarianceRatio\":[0.65,0.25]}"
        );
        run.setCreatedBy(CURRENT_USER_ID);
        run.setCreatedAt(CREATED_AT);
        run.setStartedAt(CREATED_AT.plusSeconds(1));
        run.setFinishedAt(CREATED_AT.plusSeconds(2));
        return run;
    }

    private static Community community(
            String id,
            ClusteringRun run,
            int clusterNo,
            int memberCount
    ) {
        Community community = new Community();
        community.setId(id);
        community.setRun(run);
        community.setClusterNo(clusterNo);
        community.setName("社区 " + (clusterNo + 1));
        community.setDescription("关闭 Python 的历史社区");
        community.setMemberCount(memberCount);
        community.setTopInterestsJson(
                clusterNo == 0 ? "[\"AI\",\"编程\"]" : "[\"羽毛球\"]"
        );
        community.setColor(clusterNo == 0 ? "#1677FF" : "#52C41A");
        return community;
    }

    private static CommunityMember member(
            String id,
            ClusteringRun run,
            Community community,
            UserAccount user,
            double x,
            double y,
            double distance
    ) {
        CommunityMember member = new CommunityMember();
        member.setId(id);
        member.setRun(run);
        member.setCommunity(community);
        member.setUser(user);
        member.setCoordinateX(x);
        member.setCoordinateY(y);
        member.setDistanceToCenter(distance);
        member.setAssignedAt(run.getFinishedAt());
        return member;
    }

    private void cleanClusteringData() {
        memberRepository.deleteAllInBatch();
        communityRepository.deleteAllInBatch();
        runRepository.deleteAllInBatch();
    }

    private void deleteUsers() {
        userRepository.deleteAllById(List.of(
                CURRENT_USER_ID,
                ABSENT_USER_ID,
                FIRST_USER_ID,
                SECOND_USER_ID
        ));
    }

    private static org.springframework.test.web.servlet.ResultMatcher
            fixedNoSuccessfulRun() {
        return result -> {
            assertThat(result.getResponse().getStatus()).isEqualTo(404);
            assertThat(MediaType.parseMediaType(
                    result.getResponse().getContentType()
            ).isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
            JsonNode json = new ObjectMapper().readTree(
                    result.getResponse().getContentAsString(
                            StandardCharsets.UTF_8
                    )
            );
            assertThat(json.fieldNames()).toIterable()
                    .containsExactlyInAnyOrder("code", "message", "details");
            assertThat(json.get("code").asText())
                    .isEqualTo("NO_SUCCESSFUL_RUN");
            assertThat(json.get("message").asText())
                    .isEqualTo("当前还没有可用的社区聚类结果");
            assertThat(json.get("details").isObject()).isTrue();
            assertThat(json.get("details").isEmpty()).isTrue();
        };
    }
}
