package com.example.campusactivity.repository;

import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRun;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.entity.Community;
import com.example.campusactivity.entity.CommunityMember;
import com.example.campusactivity.entity.UserAccount;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class CommunityClusteringRepositoryTest {
    @Autowired
    private ClusteringRunRepository runRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityMemberRepository memberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesAndReadsClusteringRunByVersion() {
        ClusteringRun run = run("run-save", "version-save", ClusteringRunStatus.RUNNING);
        run.setStartedAt(Instant.parse("2026-07-15T01:00:00Z"));
        run.setSampleCount(0);
        run.setParametersJson("{\"clusterCount\":2}");

        runRepository.saveAndFlush(run);
        entityManager.clear();

        ClusteringRun saved = runRepository.findByVersion("version-save").orElseThrow();
        assertThat(saved.getId()).isEqualTo("run-save");
        assertThat(saved.getAlgorithm()).isEqualTo(ClusteringAlgorithm.KMEANS);
        assertThat(saved.getClusterCount()).isEqualTo(2);
        assertThat(saved.getRandomState()).isEqualTo(42);
        assertThat(saved.getStatus()).isEqualTo(ClusteringRunStatus.RUNNING);
        assertThat(saved.getSampleCount()).isZero();
        assertThat(saved.getFeatureSchemaVersion()).isEqualTo("community-features-v1");
        assertThat(saved.getParametersJson()).isEqualTo("{\"clusterCount\":2}");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(runRepository.existsByVersion("version-save")).isTrue();
        assertThat(runRepository.existsByVersion("missing-version")).isFalse();
    }

    @Test
    void keepsParametersJsonImmutableAfterInsert() {
        ClusteringRun run = run("run-immutable-parameters", "version-immutable-parameters", ClusteringRunStatus.PENDING);
        run.setParametersJson("{}");
        runRepository.saveAndFlush(run);
        entityManager.clear();

        ClusteringRun saved = runRepository.findById(run.getId()).orElseThrow();
        saved.setParametersJson("{\"changed\":true}");
        runRepository.saveAndFlush(saved);
        entityManager.clear();

        assertThat(runRepository.findById(run.getId()).orElseThrow().getParametersJson())
                .isEqualTo("{}");
    }

    @Test
    void keepsVersionImmutableAfterInsert() {
        ClusteringRun run = run("run-immutable-version", "version-original", ClusteringRunStatus.PENDING);
        runRepository.saveAndFlush(run);
        entityManager.clear();

        ClusteringRun saved = runRepository.findById(run.getId()).orElseThrow();
        saved.setVersion("version-changed");
        runRepository.saveAndFlush(saved);
        entityManager.clear();

        assertThat(runRepository.findById(run.getId()).orElseThrow().getVersion())
                .isEqualTo("version-original");
        assertThat(runRepository.findByVersion("version-changed")).isEmpty();
    }

    @Test
    void latestIncludesOnlySuccessfulRunsWithFinishedTime() {
        ClusteringRun success = persistSuccessfulRun(
                "eligible",
                "run-success",
                Instant.parse("2026-07-15T03:00:00Z"),
                Instant.parse("2026-07-15T01:00:00Z")
        );
        persistSuccessfulRun(
                "unfinished",
                "run-success-unfinished",
                null,
                Instant.parse("2026-07-15T05:00:00Z")
        );

        ClusteringRun failed = run("run-failed", "version-failed", ClusteringRunStatus.FAILED);
        failed.setSampleCount(2);
        failed.setFinishedAt(Instant.parse("2026-07-15T06:00:00Z"));
        failed.setErrorMessage("test failure");
        runRepository.saveAndFlush(failed);

        ClusteringRun running = run("run-running", "version-running", ClusteringRunStatus.RUNNING);
        running.setSampleCount(2);
        running.setStartedAt(Instant.parse("2026-07-15T00:30:00Z"));
        runRepository.saveAndFlush(running);

        runRepository.saveAndFlush(run("run-pending", "version-pending", ClusteringRunStatus.PENDING));
        entityManager.clear();

        ClusteringRun latest = runRepository.findLatestSuccessful().orElseThrow();

        assertThat(latest.getId()).isEqualTo(success.getId());
        assertThat(latest.getStatus()).isEqualTo(ClusteringRunStatus.SUCCESS);
    }

    @Test
    void latestOrdersByFinishedAtDescending() {
        persistSuccessfulRun(
                "finished-old",
                "run-finished-old",
                Instant.parse("2026-07-15T02:00:00Z"),
                Instant.parse("2026-07-15T01:30:00Z")
        );
        ClusteringRun newer = persistSuccessfulRun(
                "finished-new",
                "run-finished-new",
                Instant.parse("2026-07-15T03:00:00Z"),
                Instant.parse("2026-07-15T01:00:00Z")
        );
        entityManager.clear();

        assertThat(runRepository.findLatestSuccessful().orElseThrow().getId())
                .isEqualTo(newer.getId());
    }

    @Test
    void latestUsesCreatedAtWhenFinishedTimesTie() {
        Instant finishedAt = Instant.parse("2026-07-15T05:00:00Z");
        persistSuccessfulRun(
                "created-old",
                "run-created-old",
                finishedAt,
                Instant.parse("2026-07-15T01:00:00Z")
        );
        ClusteringRun newer = persistSuccessfulRun(
                "created-new",
                "run-created-new",
                finishedAt,
                Instant.parse("2026-07-15T02:00:00Z")
        );
        entityManager.clear();

        assertThat(runRepository.findLatestSuccessful().orElseThrow().getId())
                .isEqualTo(newer.getId());
    }

    @Test
    void latestUsesIdDescendingWhenFinishedAndCreatedTimesTie() {
        Instant finishedAt = Instant.parse("2026-07-15T05:00:00Z");
        Instant createdAt = Instant.parse("2026-07-15T02:00:00Z");
        persistSuccessfulRun("id-a", "run-id-a", finishedAt, createdAt);
        ClusteringRun higherId = persistSuccessfulRun("id-b", "run-id-b", finishedAt, createdAt);
        entityManager.clear();

        assertThat(runRepository.findLatestSuccessful().orElseThrow().getId())
                .isEqualTo(higherId.getId());
    }

    @Test
    void findsCommunitiesInClusterNumberOrder() {
        ClusteringRun orderedRun = run("run-order", "version-order", ClusteringRunStatus.PENDING);
        orderedRun.setClusterCount(3);
        ClusteringRun run = runRepository.saveAndFlush(orderedRun);
        communityRepository.saveAndFlush(community("community-2", run, 2));
        communityRepository.saveAndFlush(community("community-0", run, 0));
        communityRepository.saveAndFlush(community("community-1", run, 1));
        entityManager.clear();

        List<Community> communities = communityRepository.findByRunOrderByClusterNoAsc(run);

        assertThat(communities)
                .extracting(Community::getClusterNo)
                .containsExactly(0, 1, 2);
        assertThat(communityRepository.findByRunAndClusterNo(run, 1))
                .get()
                .extracting(Community::getId)
                .isEqualTo("community-1");
    }

    @Test
    void queriesMembershipByRunCommunityAndUser() {
        UserAccount user = userRepository.saveAndFlush(user("user-query"));
        ClusteringRun run = runRepository.saveAndFlush(
                run("run-query", "version-query", ClusteringRunStatus.PENDING)
        );
        Community community = communityRepository.saveAndFlush(community("community-query", run, 0));
        CommunityMember member = memberRepository.saveAndFlush(
                member("member-query", run, community, user)
        );
        entityManager.clear();

        assertThat(memberRepository.findByRun(run))
                .extracting(CommunityMember::getId)
                .containsExactly(member.getId());
        assertThat(memberRepository.findByCommunity(community))
                .extracting(CommunityMember::getId)
                .containsExactly(member.getId());
        assertThat(memberRepository.findByRunAndUser(run, user))
                .get()
                .extracting(CommunityMember::getId)
                .isEqualTo(member.getId());
        assertThat(memberRepository.existsByRunAndUser(run, user)).isTrue();
    }

    @Test
    void rejectsNonFiniteCoordinateXOnFlush() {
        MembershipFixture fixture = membershipFixture("non-finite-x");
        CommunityMember member = member(
                "member-non-finite-x",
                fixture.run(),
                fixture.community(),
                fixture.user()
        );
        member.setCoordinateX(Double.NaN);

        assertThatThrownBy(() -> memberRepository.saveAndFlush(member))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rejectsNonFiniteCoordinateYOnFlush() {
        MembershipFixture fixture = membershipFixture("non-finite-y");
        CommunityMember member = member(
                "member-non-finite-y",
                fixture.run(),
                fixture.community(),
                fixture.user()
        );
        member.setCoordinateY(Double.POSITIVE_INFINITY);

        assertThatThrownBy(() -> memberRepository.saveAndFlush(member))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rejectsNonFiniteDistanceOnFlush() {
        MembershipFixture fixture = membershipFixture("non-finite-distance");
        CommunityMember member = member(
                "member-non-finite-distance",
                fixture.run(),
                fixture.community(),
                fixture.user()
        );
        member.setDistanceToCenter(Double.POSITIVE_INFINITY);

        assertThatThrownBy(() -> memberRepository.saveAndFlush(member))
                .isInstanceOf(ConstraintViolationException.class);
    }

    private MembershipFixture membershipFixture(String suffix) {
        UserAccount user = userRepository.saveAndFlush(user("user-" + suffix));
        ClusteringRun run = runRepository.saveAndFlush(
                run("run-" + suffix, "version-" + suffix, ClusteringRunStatus.PENDING)
        );
        Community community = communityRepository.saveAndFlush(
                community("community-" + suffix, run, 0)
        );
        return new MembershipFixture(run, community, user);
    }

    private ClusteringRun persistSuccessfulRun(
            String suffix,
            String runId,
            Instant finishedAt,
            Instant createdAt
    ) {
        ClusteringRun run = run(runId, "version-" + suffix, ClusteringRunStatus.SUCCESS);
        run.setSampleCount(2);
        run.setStartedAt(Instant.parse("2026-07-15T00:30:00Z"));
        run.setFinishedAt(finishedAt);
        run.setCreatedAt(createdAt);
        run.setMetricsJson("{}");
        runRepository.saveAndFlush(run);

        UserAccount firstUser = userRepository.saveAndFlush(user("user-" + suffix + "-a"));
        UserAccount secondUser = userRepository.saveAndFlush(user("user-" + suffix + "-b"));
        Community firstCommunity = communityRepository.saveAndFlush(
                community("community-" + suffix + "-a", run, 0)
        );
        Community secondCommunity = communityRepository.saveAndFlush(
                community("community-" + suffix + "-b", run, 1)
        );
        memberRepository.saveAndFlush(
                member("member-" + suffix + "-a", run, firstCommunity, firstUser)
        );
        memberRepository.saveAndFlush(
                member("member-" + suffix + "-b", run, secondCommunity, secondUser)
        );
        return run;
    }

    private static ClusteringRun run(String id, String version, ClusteringRunStatus status) {
        ClusteringRun run = new ClusteringRun();
        run.setId(id);
        run.setVersion(version);
        run.setAlgorithm(ClusteringAlgorithm.KMEANS);
        run.setClusterCount(2);
        run.setRandomState(42);
        run.setStatus(status);
        run.setSampleCount(null);
        run.setFeatureSchemaVersion("community-features-v1");
        run.setParametersJson("{}");
        run.setCreatedBy("admin-test");
        return run;
    }

    private static Community community(String id, ClusteringRun run, int clusterNo) {
        Community community = new Community();
        community.setId(id);
        community.setRun(run);
        community.setClusterNo(clusterNo);
        community.setName("社区 " + (clusterNo + 1));
        community.setMemberCount(1);
        community.setTopInterestsJson("[]");
        community.setColor("#1677FF");
        return community;
    }

    private static CommunityMember member(
            String id,
            ClusteringRun run,
            Community community,
            UserAccount user
    ) {
        CommunityMember member = new CommunityMember();
        member.setId(id);
        member.setRun(run);
        member.setCommunity(community);
        member.setUser(user);
        member.setCoordinateX(50.0);
        member.setCoordinateY(50.0);
        member.setDistanceToCenter(0.0);
        member.setAssignedAt(Instant.parse("2026-07-15T03:00:00Z"));
        return member;
    }

    private static UserAccount user(String id) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setPassword("test-password");
        user.setName("测试用户");
        return user;
    }

    private record MembershipFixture(ClusteringRun run, Community community, UserAccount user) {
    }
}
