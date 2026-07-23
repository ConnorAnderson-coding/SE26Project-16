package com.example.demo.controller;

import com.example.demo.entity.ClusteringAlgorithm;
import com.example.demo.entity.ClusteringRun;
import com.example.demo.entity.ClusteringRunStatus;
import com.example.demo.entity.Community;
import com.example.demo.entity.CommunityMember;
import com.example.demo.repository.ClusteringRunRepository;
import com.example.demo.repository.CommunityMemberRepository;
import com.example.demo.repository.CommunityRepository;
import com.example.demo.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CommunityClusteringIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ClusteringRunRepository runRepository;
    @Autowired
    private CommunityRepository communityRepository;
    @Autowired
    private CommunityMemberRepository memberRepository;

    private TestScenario scenario;
    private ClusteringRun run;
    private Community firstCommunity;

    @BeforeEach
    void setUp() {
        scenario = createScenario();
        run = transactionTemplate.execute(status -> seedSuccessfulRun());
        firstCommunity = communityRepository.findByRunOrderByClusterNoAsc(run).getFirst();
    }

    @Test
    void latestReturnsAnonymousPointsAndCurrentUserMarker() throws Exception {
        var response = authGet(scenario.studentToken(), "/api/v1/community-clustering/latest")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.run.runId").value(run.getId()))
                .andExpect(jsonPath("$.data.communities[0].points[0].pointId").isString())
                .andExpect(jsonPath("$.data.communities[0].points[0].currentUser").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(scenario.student().getId());
        assertThat(response).doesNotContain(scenario.organizer().getId());
        assertThat(response).doesNotContain("passwordHash");
    }

    @Test
    void meReturnsOnlyAuthenticatedUsersMembership() throws Exception {
        authGet(scenario.studentToken(), "/api/v1/community-clustering/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value(run.getId()))
                .andExpect(jsonPath("$.data.membership.communityId").value(firstCommunity.getId()))
                .andExpect(jsonPath("$.data.membership.pointId").isString());
    }

    @Test
    void adminEndpointsRejectStudentAndExposeOnlyMinimalMemberFields() throws Exception {
        authGet(scenario.studentToken(), "/api/v1/admin/community-clustering/runs")
                .andExpect(status().isForbidden());

        authGet(scenario.adminToken(), "/api/v1/admin/community-clustering/runs/" + run.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.metrics.inertia").value(1.25));

        var response = authGet(scenario.adminToken(), "/api/v1/admin/community-clustering/communities/"
                        + firstCommunity.getId() + "/members")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].userId").value(scenario.student().getId()))
                .andExpect(jsonPath("$.data.items[0].name").value(scenario.student().getName()))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("passwordHash");
        assertThat(response).doesNotContain("authorities");
        assertThat(response).doesNotContain("interests");
    }

    @Test
    void submissionIsUnavailableWhenPythonCapabilityIsDisabled() throws Exception {
        authPost(scenario.adminToken(), "/api/v1/admin/community-clustering/runs", Map.of("clusterCount", 2))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));
    }

    @Test
    void submissionRejectsIdentityInjectionBeforeCapabilityCheck() throws Exception {
        authPost(scenario.adminToken(), "/api/v1/admin/community-clustering/runs",
                        Map.of("clusterCount", 2, "createdBy", scenario.student().getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void clusteringRoutesRequireJwt() throws Exception {
        mockMvc.perform(get("/api/v1/community-clustering/latest"))
                .andExpect(status().isForbidden());
    }

    private ClusteringRun seedSuccessfulRun() {
        LocalDateTime now = LocalDateTime.now();
        String suffix = UUID.randomUUID().toString();
        ClusteringRun savedRun = new ClusteringRun();
        savedRun.setId("run-" + suffix);
        savedRun.setVersion("cc-test-" + suffix);
        savedRun.setAlgorithm(ClusteringAlgorithm.KMEANS);
        savedRun.setClusterCount(2);
        savedRun.setRandomState(42);
        savedRun.setStatus(ClusteringRunStatus.SUCCESS);
        savedRun.setActiveSlot(null);
        savedRun.setSampleCount(2);
        savedRun.setFeatureDimension(8);
        savedRun.setFeatureSchemaVersion("community-features-v2");
        savedRun.setParameters(Map.of("clusterCount", 2));
        savedRun.setFeatureManifest(Map.of("schemaVersion", "community-features-v2"));
        savedRun.setMetrics(Map.of("inertia", 1.25, "pcaExplainedVarianceRatio", List.of(0.6, 0.2)));
        savedRun.setStartedAt(now.minusSeconds(2));
        savedRun.setFinishedAt(now.minusSeconds(1));
        savedRun.setCreatedBy(scenario.admin().getId());
        savedRun.setCreatedAt(now.minusSeconds(3));
        savedRun = runRepository.saveAndFlush(savedRun);

        Community first = community(savedRun, 0, "#1677FF");
        Community second = community(savedRun, 1, "#52C41A");
        List<Community> savedCommunities = communityRepository.saveAllAndFlush(List.of(first, second));
        memberRepository.saveAllAndFlush(List.of(
                member(savedRun, savedCommunities.get(0), scenario.student(), 10.0, 20.0, 0.2),
                member(savedRun, savedCommunities.get(1), scenario.organizer(), 80.0, 70.0, 0.3)
        ));
        return savedRun;
    }

    private static Community community(ClusteringRun run, int clusterNo, String color) {
        Community community = new Community();
        community.setId(UUID.randomUUID().toString());
        community.setRun(run);
        community.setClusterNo(clusterNo);
        community.setName("社区 " + (clusterNo + 1));
        community.setDescription("测试社区");
        community.setMemberCount(1);
        community.setTopInterests(List.of("测试兴趣"));
        community.setColor(color);
        return community;
    }

    private static CommunityMember member(
            ClusteringRun run,
            Community community,
            com.example.demo.entity.User user,
            double x,
            double y,
            double distance
    ) {
        CommunityMember member = new CommunityMember();
        member.setId(UUID.randomUUID().toString());
        member.setRun(run);
        member.setCommunity(community);
        member.setUser(user);
        member.setCoordinateX(x);
        member.setCoordinateY(y);
        member.setDistanceToCenter(distance);
        member.setAssignedAt(LocalDateTime.now());
        return member;
    }
}
