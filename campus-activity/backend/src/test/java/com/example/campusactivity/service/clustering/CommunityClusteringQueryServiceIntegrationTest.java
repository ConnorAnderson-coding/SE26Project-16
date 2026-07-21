package com.example.campusactivity.service.clustering;

import com.example.campusactivity.dto.clustering.CurrentUserClusteringResponse;
import com.example.campusactivity.dto.clustering.ClusteringRunPageResponse;
import com.example.campusactivity.dto.clustering.CommunityMembersPageResponse;
import com.example.campusactivity.dto.clustering.LatestClusteringResponse;
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
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "community-clustering.python.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session.events.log=false"
})
class CommunityClusteringQueryServiceIntegrationTest {
    private static final Instant BASE_TIME = Instant.parse("2026-07-17T02:00:00Z");

    @Autowired
    private CommunityClusteringQueryService queryService;
    @Autowired
    private ClusteringRunRepository runRepository;
    @Autowired
    private CommunityRepository communityRepository;
    @Autowired
    private CommunityMemberRepository memberRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void cleanClusteringData() {
        memberRepository.deleteAllInBatch();
        communityRepository.deleteAllInBatch();
        runRepository.deleteAllInBatch();
    }

    @Test
    void readsHistoricalSuccessWhenPythonIsDisabledWithStableOrderingAndThreeQueries() {
        createThreePointSuccess(
                "latest-a",
                "version-a",
                BASE_TIME,
                BASE_TIME.plusSeconds(20),
                "integration-user-a"
        );
        createThreePointSuccess(
                "latest-b",
                "version-b",
                BASE_TIME,
                BASE_TIME.plusSeconds(20),
                "integration-user-current"
        );
        runRepository.saveAndFlush(failedRun(
                "newer-failed",
                "failed-version",
                BASE_TIME.plusSeconds(30),
                BASE_TIME.plusSeconds(40)
        ));

        Statistics statistics = statistics();
        statistics.clear();
        LatestClusteringResponse response =
                queryService.findLatestClustering("integration-user-current");

        assertThat(AopUtils.isAopProxy(queryService)).isTrue();
        assertThat(response.run().runId()).isEqualTo("latest-b");
        assertThat(response.communities())
                .extracting(community -> community.clusterNo())
                .containsExactly(0, 1);
        assertThat(response.communities().get(0).points())
                .extracting(point -> point.pointId())
                .containsExactly("point-\uE000-latest-b", "point-\uD800\uDC00-latest-b");
        assertThat(response.communities())
                .flatExtracting(community -> community.points())
                .filteredOn(point -> point.currentUser())
                .singleElement()
                .satisfies(point -> assertThat(point.pointId())
                        .isEqualTo("point-current-latest-b"));
        assertThat(response.toString())
                .doesNotContain("integration-user-current", "userId", "distanceToCenter");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);

        statistics.clear();
        LatestClusteringResponse repeated =
                queryService.findLatestClustering("integration-user-current");
        assertThat(repeated).isEqualTo(response);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void queryCountDoesNotGrowWithCommunityCount() {
        ClusteringRun run = successfulRun(
                "four-cluster-run",
                "four-cluster-version",
                4,
                4,
                BASE_TIME.plusSeconds(100),
                BASE_TIME.plusSeconds(120)
        );
        runRepository.saveAndFlush(run);
        for (int clusterNo = 0; clusterNo < 4; clusterNo++) {
            UserAccount user = saveUser("four-cluster-user-" + clusterNo);
            Community community = communityRepository.saveAndFlush(
                    community(
                            "four-community-" + clusterNo,
                            run,
                            clusterNo,
                            1
                    )
            );
            memberRepository.saveAndFlush(member(
                    "four-point-" + clusterNo,
                    run,
                    community,
                    user,
                    clusterNo * 10.0,
                    100.0 - clusterNo * 10.0,
                    clusterNo
            ));
        }

        Statistics statistics = statistics();
        statistics.clear();
        LatestClusteringResponse response =
                queryService.findLatestClustering("four-cluster-user-0");

        assertThat(response.communities()).hasSize(4);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void returnsCurrentUserMembershipOrNullThroughSafeProjection() {
        createThreePointSuccess(
                "membership-run",
                "membership-version",
                BASE_TIME,
                BASE_TIME.plusSeconds(20),
                "membership-user"
        );

        CurrentUserClusteringResponse present =
                queryService.findCurrentUserClustering("membership-user");
        CurrentUserClusteringResponse absent =
                queryService.findCurrentUserClustering("not-in-sample");

        assertThat(present.runId()).isEqualTo("membership-run");
        assertThat(present.membership()).isNotNull();
        assertThat(present.membership().pointId())
                .isEqualTo("point-current-membership-run");
        assertThat(present.membership().distanceToCenter()).isEqualTo(0.3);
        assertThat(present.toString()).doesNotContain("membership-user", "userId");
        assertThat(absent.membership()).isNull();
    }

    @Test
    void returnsNoSuccessfulRunWhenOnlyFailedHistoryExists() {
        runRepository.saveAndFlush(failedRun(
                "failed-only",
                "failed-only-version",
                BASE_TIME,
                BASE_TIME.plusSeconds(10)
        ));

        assertThatThrownBy(() -> queryService.findLatestClustering("some-user"))
                .isInstanceOfSatisfying(ClusteringQueryException.class, exception -> {
                    assertThat(exception.getCode())
                            .isEqualTo(ClusteringQueryCode.NO_SUCCESSFUL_RUN);
                    assertThat(exception.getCause()).isNull();
                });
    }

    @Test
    void repositoryProjectionQueriesReturnOnlySafeScalarShapes() {
        createThreePointSuccess(
                "projection-run",
                "projection-version",
                BASE_TIME,
                BASE_TIME.plusSeconds(20),
                "projection-user"
        );

        assertThat(runRepository.findQueryProjectionById("projection-run"))
                .hasValueSatisfying(run -> {
                    assertThat(run.getId()).isEqualTo("projection-run");
                    assertThat(run.getStatus()).isEqualTo(ClusteringRunStatus.SUCCESS);
                });
        assertThat(communityRepository.findQueryProjectionsByRunId("projection-run"))
                .extracting(community -> community.getClusterNo())
                .containsExactly(0, 1);
        assertThat(memberRepository.findPointProjectionsByRunId(
                "projection-run",
                "projection-user"
        )).filteredOn(point -> point.getCurrentUser())
                .singleElement()
                .satisfies(point -> assertThat(point.getPointId())
                        .isEqualTo("point-current-projection-run"));
        assertThat(memberRepository.findMembershipProjection(
                "projection-run",
                "projection-user"
        )).hasValueSatisfying(membership -> {
            assertThat(membership.getCommunityId()).isEqualTo("community-1-projection-run");
            assertThat(membership.getDistanceToCenter()).isEqualTo(0.3);
        });
    }

    @Test
    void runHistoryUsesStableDatabasePaginationAndTwoQueries() {
        runRepository.saveAndFlush(failedRun(
                "run-a", "page-version-a", BASE_TIME, BASE_TIME.plusSeconds(10)
        ));
        runRepository.saveAndFlush(failedRun(
                "run-b", "page-version-b", BASE_TIME.plusSeconds(20),
                BASE_TIME.plusSeconds(30)
        ));
        runRepository.saveAndFlush(failedRun(
                "run-c", "page-version-c", BASE_TIME.plusSeconds(20),
                BASE_TIME.plusSeconds(31)
        ));
        runRepository.saveAndFlush(failedRun(
                "run-d", "page-version-d", BASE_TIME.plusSeconds(40),
                BASE_TIME.plusSeconds(50)
        ));

        Statistics statistics = statistics();
        statistics.clear();
        ClusteringRunPageResponse response = queryService.findRuns("1", "2");

        assertThat(response.items())
                .extracting(item -> item.runId())
                .containsExactly("run-b", "run-a");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(4);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.toString()).doesNotContain(
                "parametersJson", "metricsJson", "errorMessage", "activeSlot"
        );
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({
            "-1,20",
            "0,0",
            "0,101",
            "not-a-number,20",
            "0,not-a-number"
    })
    void rejectsInvalidRunHistoryPagination(String page, String size) {
        assertThatThrownBy(() -> queryService.findRuns(page, size))
                .isInstanceOfSatisfying(ClusteringQueryException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(ClusteringQueryCode.INVALID_PAGE_REQUEST)
                );
    }

    @Test
    void adminMembersUseThreeFixedQueriesWithStableDatabasePagination() {
        createThreePointSuccess(
                "admin-member-run",
                "admin-member-version",
                BASE_TIME,
                BASE_TIME.plusSeconds(20),
                "admin-member-current"
        );

        Statistics statistics = statistics();
        statistics.clear();
        CommunityMembersPageResponse first = queryService.findCommunityMembers(
                "community-0-admin-member-run", "0", "1"
        );

        assertThat(first.community().runId()).isEqualTo("admin-member-run");
        assertThat(first.community().clusterNo()).isZero();
        assertThat(first.community().memberCount()).isEqualTo(2);
        assertThat(first.items()).singleElement().satisfies(member -> {
            assertThat(member.userId())
                    .isEqualTo("private-use-user-admin-member-run");
            assertThat(member.name()).isEqualTo("测试用户");
            assertThat(member.college()).isEqualTo("软件学院");
            assertThat(member.grade()).isEqualTo("2026");
            assertThat(member.pointId())
                    .isEqualTo("point-\uE000-admin-member-run");
            assertThat(member.distanceToCenter()).isEqualTo(0.6);
        });
        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.toString()).doesNotContain(
                "password", "role", "friends", "interests", "assignedAt"
        );
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);

        CommunityMembersPageResponse second = queryService.findCommunityMembers(
                "community-0-admin-member-run", "1", "1"
        );
        assertThat(second.items()).singleElement().satisfies(member ->
                assertThat(member.userId())
                        .isEqualTo("supplementary-user-admin-member-run")
        );
    }

    @Test
    void adminMemberQueryRejectsInvalidOrMissingCommunity() {
        assertThatThrownBy(() -> queryService.findCommunityMembers(" ", "0", "20"))
                .isInstanceOfSatisfying(ClusteringQueryException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(ClusteringQueryCode.INVALID_COMMUNITY_ID)
                );
        assertThatThrownBy(() -> queryService.findCommunityMembers(
                "missing-community", "0", "20"
        )).isInstanceOfSatisfying(ClusteringQueryException.class, exception ->
                assertThat(exception.getCode())
                        .isEqualTo(ClusteringQueryCode.COMMUNITY_NOT_FOUND)
        );
    }

    private void createThreePointSuccess(
            String runId,
            String version,
            Instant createdAt,
            Instant finishedAt,
            String currentUserId
    ) {
        ClusteringRun run = runRepository.saveAndFlush(successfulRun(
                runId,
                version,
                2,
                3,
                createdAt,
                finishedAt
        ));
        Community second = communityRepository.saveAndFlush(
                community("community-1-" + runId, run, 1, 1)
        );
        Community first = communityRepository.saveAndFlush(
                community("community-0-" + runId, run, 0, 2)
        );

        UserAccount supplementaryPointUser = saveUser("supplementary-user-" + runId);
        UserAccount privateUsePointUser = saveUser("private-use-user-" + runId);
        UserAccount currentUser = saveUser(currentUserId);

        List<CommunityMember> members = new ArrayList<>();
        members.add(member(
                "point-\uD800\uDC00-" + runId,
                run,
                first,
                supplementaryPointUser,
                20.0,
                70.0,
                0.8
        ));
        members.add(member(
                "point-\uE000-" + runId,
                run,
                first,
                privateUsePointUser,
                30.0,
                80.0,
                0.6
        ));
        members.add(member(
                "point-current-" + runId,
                run,
                second,
                currentUser,
                90.0,
                10.0,
                0.3
        ));
        memberRepository.saveAllAndFlush(members);
    }

    private static ClusteringRun successfulRun(
            String id,
            String version,
            int clusterCount,
            int sampleCount,
            Instant createdAt,
            Instant finishedAt
    ) {
        ClusteringRun run = baseRun(
                id,
                version,
                ClusteringRunStatus.SUCCESS,
                clusterCount,
                sampleCount,
                createdAt
        );
        run.setStartedAt(createdAt.plusSeconds(1));
        run.setFinishedAt(finishedAt);
        run.setMetricsJson(
                "{\"inertia\":2.5,\"pcaExplainedVarianceRatio\":[0.65,0.25]}"
        );
        return run;
    }

    private static ClusteringRun failedRun(
            String id,
            String version,
            Instant createdAt,
            Instant finishedAt
    ) {
        ClusteringRun run = baseRun(
                id,
                version,
                ClusteringRunStatus.FAILED,
                2,
                null,
                createdAt
        );
        run.setStartedAt(createdAt.plusSeconds(1));
        run.setFinishedAt(finishedAt);
        run.setErrorMessage(ClusteringRunFailureCode.INTERNAL_ERROR.errorMessage());
        return run;
    }

    private static ClusteringRun baseRun(
            String id,
            String version,
            ClusteringRunStatus status,
            int clusterCount,
            Integer sampleCount,
            Instant createdAt
    ) {
        ClusteringRun run = new ClusteringRun();
        run.setId(id);
        run.setVersion(version);
        run.setAlgorithm(ClusteringAlgorithm.KMEANS);
        run.setClusterCount(clusterCount);
        run.setRandomState(42);
        run.setStatus(status);
        run.setActiveSlot(null);
        run.setSampleCount(sampleCount);
        run.setFeatureSchemaVersion("community-features-v1");
        run.setParametersJson("{}");
        run.setCreatedBy("integration-admin");
        run.setCreatedAt(createdAt);
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
        community.setDescription("集成测试社区");
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

    private UserAccount saveUser(String id) {
        return userRepository.findById(id).orElseGet(() -> {
            UserAccount user = new UserAccount();
            user.setId(id);
            user.setPassword("test-password");
            user.setName("测试用户");
            user.setCollege("软件学院");
            user.setGrade("2026");
            return userRepository.saveAndFlush(user);
        });
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
