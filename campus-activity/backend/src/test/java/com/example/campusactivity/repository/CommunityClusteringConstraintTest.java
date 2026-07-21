package com.example.campusactivity.repository;

import com.example.campusactivity.entity.Activity;
import com.example.campusactivity.entity.CheckInRecord;
import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRun;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.entity.Community;
import com.example.campusactivity.entity.CommunityMember;
import com.example.campusactivity.entity.Favorite;
import com.example.campusactivity.entity.Feedback;
import com.example.campusactivity.entity.Signup;
import com.example.campusactivity.entity.UserAccount;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.properties.jakarta.persistence.validation.mode=none")
class CommunityClusteringConstraintTest {
    @Autowired
    private ClusteringRunRepository runRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityMemberRepository memberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private SignupRepository signupRepository;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private CheckInRepository checkInRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void rejectsDuplicateRunVersionOnFlush() {
        runRepository.saveAndFlush(terminalRun("run-version-1", "duplicate-version"));

        assertThatThrownBy(() -> runRepository.saveAndFlush(
                terminalRun("run-version-2", "duplicate-version")
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSecondActiveSlotOnFlush() {
        runRepository.saveAndFlush(run("run-active-1", "version-active-1"));

        assertThatThrownBy(() -> runRepository.saveAndFlush(
                run("run-active-2", "version-active-2")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsMultipleTerminalRunsWithNullActiveSlot() {
        runRepository.saveAndFlush(terminalRun("run-terminal-1", "version-terminal-1"));
        ClusteringRun success = terminalRun("run-terminal-2", "version-terminal-2");
        success.setStatus(ClusteringRunStatus.SUCCESS);
        success.setMetricsJson("{}");
        success.setErrorMessage(null);
        runRepository.saveAndFlush(success);

        assertThat(runRepository.count()).isEqualTo(2);
    }

    @Test
    void rejectsPendingWithoutActiveSlotOnFlush() {
        ClusteringRun run = run("run-pending-null-slot", "version-pending-null-slot");
        run.setActiveSlot(null);

        assertThatThrownBy(() -> runRepository.saveAndFlush(run))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsRunningWithoutActiveSlotOnFlush() {
        ClusteringRun run = run("run-running-null-slot", "version-running-null-slot");
        run.setStatus(ClusteringRunStatus.RUNNING);
        run.setActiveSlot(null);

        assertThatThrownBy(() -> runRepository.saveAndFlush(run))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSuccessWithGlobalActiveSlotOnFlush() {
        ClusteringRun run = terminalRun("run-success-global-slot", "version-success-global-slot");
        run.setStatus(ClusteringRunStatus.SUCCESS);
        run.setMetricsJson("{}");
        run.setErrorMessage(null);
        run.setActiveSlot(ClusteringRun.GLOBAL_ACTIVE_SLOT);

        assertThatThrownBy(() -> runRepository.saveAndFlush(run))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsFailedWithGlobalActiveSlotOnFlush() {
        ClusteringRun run = terminalRun("run-failed-global-slot", "version-failed-global-slot");
        run.setActiveSlot(ClusteringRun.GLOBAL_ACTIVE_SLOT);

        assertThatThrownBy(() -> runRepository.saveAndFlush(run))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsNullSampleCountOnFlush() {
        ClusteringRun run = run("run-sample-null", "version-sample-null");
        run.setSampleCount(null);

        runRepository.saveAndFlush(run);
        entityManager.clear();

        assertThat(runRepository.findById(run.getId()).orElseThrow().getSampleCount()).isNull();
    }

    @Test
    void allowsZeroSampleCountOnFlush() {
        ClusteringRun run = run("run-sample-zero", "version-sample-zero");
        run.setSampleCount(0);

        runRepository.saveAndFlush(run);
        entityManager.clear();

        assertThat(runRepository.findById(run.getId()).orElseThrow().getSampleCount()).isZero();
    }

    @Test
    void rejectsNegativeSampleCountOnFlush() {
        ClusteringRun run = run("run-sample-negative", "version-sample-negative");
        run.setSampleCount(-1);

        assertThatThrownBy(() -> runRepository.saveAndFlush(run))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsClusterCountBelowTwoOnFlush() {
        ClusteringRun run = run("run-cluster-count", "version-cluster-count");
        run.setClusterCount(1);

        assertThatThrownBy(() -> runRepository.saveAndFlush(run))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsEmptyParametersJsonObjectOnFlush() {
        ClusteringRun run = run("run-parameters-empty", "version-parameters-empty");
        run.setParametersJson("{}");

        runRepository.saveAndFlush(run);
        entityManager.clear();

        assertThat(runRepository.findById(run.getId()).orElseThrow().getParametersJson())
                .isEqualTo("{}");
    }

    @Test
    void rejectsNullParametersJsonOnFlush() {
        ClusteringRun run = run("run-parameters-null", "version-parameters-null");
        run.setParametersJson(null);

        assertThatThrownBy(() -> runRepository.saveAndFlush(run))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateClusterNumberWithinSameRunOnFlush() {
        ClusteringRun run = runRepository.saveAndFlush(run("run-cluster-duplicate", "version-cluster-duplicate"));
        communityRepository.saveAndFlush(community("community-cluster-1", run, 0));

        assertThatThrownBy(() -> communityRepository.saveAndFlush(
                community("community-cluster-2", run, 0)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameClusterNumberInDifferentRuns() {
        ClusteringRun firstRun = runRepository.saveAndFlush(
                terminalRun("run-cluster-a", "version-cluster-a")
        );
        ClusteringRun secondRun = runRepository.saveAndFlush(
                terminalRun("run-cluster-b", "version-cluster-b")
        );

        communityRepository.saveAndFlush(community("community-cluster-a", firstRun, 0));
        communityRepository.saveAndFlush(community("community-cluster-b", secondRun, 0));

        assertThat(communityRepository.count()).isEqualTo(2);
    }

    @Test
    void rejectsNegativeClusterNumberOnFlush() {
        ClusteringRun run = runRepository.saveAndFlush(
                run("run-cluster-negative", "version-cluster-negative")
        );
        Community community = community("community-cluster-negative", run, -1);

        assertThatThrownBy(() -> communityRepository.saveAndFlush(community))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsPositiveMemberCountOnFlush() {
        ClusteringRun run = runRepository.saveAndFlush(
                run("run-member-count-positive", "version-member-count-positive")
        );
        Community community = community("community-member-count-positive", run, 0);
        community.setMemberCount(1);

        communityRepository.saveAndFlush(community);
        entityManager.clear();

        assertThat(communityRepository.findById(community.getId()).orElseThrow().getMemberCount())
                .isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveMemberCountOnFlush(int memberCount) {
        ClusteringRun run = runRepository.saveAndFlush(
                run("run-member-count-" + memberCount, "version-member-count-" + memberCount)
        );
        Community community = community("community-member-count-" + memberCount, run, 0);
        community.setMemberCount(memberCount);

        assertThatThrownBy(() -> communityRepository.saveAndFlush(community))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsEmptyTopInterestsJsonArrayOnFlush() {
        ClusteringRun run = runRepository.saveAndFlush(
                run("run-interests-empty", "version-interests-empty")
        );
        Community community = community("community-interests-empty", run, 0);
        community.setTopInterestsJson("[]");

        communityRepository.saveAndFlush(community);
        entityManager.clear();

        assertThat(communityRepository.findById(community.getId()).orElseThrow().getTopInterestsJson())
                .isEqualTo("[]");
    }

    @Test
    void rejectsNullTopInterestsJsonOnFlush() {
        ClusteringRun run = runRepository.saveAndFlush(
                run("run-interests-null", "version-interests-null")
        );
        Community community = community("community-interests-null", run, 0);
        community.setTopInterestsJson(null);

        assertThatThrownBy(() -> communityRepository.saveAndFlush(community))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateUserWithinSameRunOnFlush() {
        UserAccount user = userRepository.saveAndFlush(user("user-member-duplicate"));
        ClusteringRun run = runRepository.saveAndFlush(run("run-member-duplicate", "version-member-duplicate"));
        Community firstCommunity = communityRepository.saveAndFlush(
                community("community-member-1", run, 0)
        );
        Community secondCommunity = communityRepository.saveAndFlush(
                community("community-member-2", run, 1)
        );
        memberRepository.saveAndFlush(member("member-duplicate-1", run, firstCommunity, user));

        assertThatThrownBy(() -> memberRepository.saveAndFlush(
                member("member-duplicate-2", run, secondCommunity, user)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameUserInDifferentRuns() {
        UserAccount user = userRepository.saveAndFlush(user("user-cross-run"));
        ClusteringRun firstRun = runRepository.saveAndFlush(
                terminalRun("run-user-a", "version-user-a")
        );
        ClusteringRun secondRun = runRepository.saveAndFlush(
                terminalRun("run-user-b", "version-user-b")
        );
        Community firstCommunity = communityRepository.saveAndFlush(community("community-user-a", firstRun, 0));
        Community secondCommunity = communityRepository.saveAndFlush(community("community-user-b", secondRun, 0));

        memberRepository.saveAndFlush(member("member-user-a", firstRun, firstCommunity, user));
        memberRepository.saveAndFlush(member("member-user-b", secondRun, secondCommunity, user));

        assertThat(memberRepository.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 100.01})
    void rejectsCoordinateXOutsideRangeOnFlush(double coordinateX) {
        MembershipFixture fixture = membershipFixture("coordinate-x");
        CommunityMember member = member("member-coordinate-x", fixture.run(), fixture.community(), fixture.user());
        member.setCoordinateX(coordinateX);

        assertThatThrownBy(() -> memberRepository.saveAndFlush(member))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 100.01})
    void rejectsCoordinateYOutsideRangeOnFlush(double coordinateY) {
        MembershipFixture fixture = membershipFixture("coordinate-y");
        CommunityMember member = member("member-coordinate-y", fixture.run(), fixture.community(), fixture.user());
        member.setCoordinateY(coordinateY);

        assertThatThrownBy(() -> memberRepository.saveAndFlush(member))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNegativeDistanceOnFlush() {
        MembershipFixture fixture = membershipFixture("distance");
        CommunityMember member = member("member-distance", fixture.run(), fixture.community(), fixture.user());
        member.setDistanceToCenter(-0.01);

        assertThatThrownBy(() -> memberRepository.saveAndFlush(member))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsCommunityFromDifferentRunOnFlush() {
        UserAccount user = userRepository.saveAndFlush(user("user-mismatched-run"));
        ClusteringRun memberRun = runRepository.saveAndFlush(
                terminalRun("run-member", "version-member")
        );
        ClusteringRun communityRun = runRepository.saveAndFlush(
                terminalRun("run-community", "version-community")
        );
        Community community = communityRepository.saveAndFlush(
                community("community-mismatched-run", communityRun, 0)
        );

        assertThatThrownBy(() -> memberRepository.saveAndFlush(
                member("member-mismatched-run", memberRun, community, user)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingRunCascadesDerivedDataButPreservesUserAndBusinessData() {
        UserAccount user = userRepository.saveAndFlush(user("user-cascade"));
        Activity activity = activityRepository.saveAndFlush(activity("activity-cascade", user.getId()));
        Signup signup = signupRepository.saveAndFlush(signup("signup-cascade", activity.getId(), user.getId()));
        Favorite favorite = favoriteRepository.saveAndFlush(favorite("favorite-cascade", activity.getId(), user.getId()));
        Feedback feedback = feedbackRepository.saveAndFlush(feedback("feedback-cascade", activity.getId(), user.getId()));
        CheckInRecord checkIn = checkInRepository.saveAndFlush(checkIn("checkin-cascade", activity.getId(), user.getId()));
        ClusteringRun run = runRepository.saveAndFlush(run("run-cascade", "version-cascade"));
        Community community = communityRepository.saveAndFlush(community("community-cascade", run, 0));
        CommunityMember member = memberRepository.saveAndFlush(
                member("member-cascade", run, community, user)
        );

        runRepository.delete(run);
        runRepository.flush();
        entityManager.clear();

        assertThat(runRepository.findById(run.getId())).isEmpty();
        assertThat(communityRepository.findById(community.getId())).isEmpty();
        assertThat(memberRepository.findById(member.getId())).isEmpty();
        assertThat(userRepository.findById(user.getId())).isPresent();
        assertThat(activityRepository.findById(activity.getId())).isPresent();
        assertThat(signupRepository.findById(signup.getId())).isPresent();
        assertThat(favoriteRepository.findById(favorite.getId())).isPresent();
        assertThat(feedbackRepository.findById(feedback.getId())).isPresent();
        assertThat(checkInRepository.findById(checkIn.getId())).isPresent();
    }

    @Test
    void deletingMembershipDoesNotDeleteUser() {
        MembershipFixture fixture = membershipFixture("member-delete");
        CommunityMember member = memberRepository.saveAndFlush(
                member("member-delete", fixture.run(), fixture.community(), fixture.user())
        );

        memberRepository.delete(member);
        memberRepository.flush();
        entityManager.clear();

        assertThat(memberRepository.findById(member.getId())).isEmpty();
        assertThat(userRepository.findById(fixture.user().getId())).isPresent();
        assertThat(runRepository.findById(fixture.run().getId())).isPresent();
        assertThat(communityRepository.findById(fixture.community().getId())).isPresent();
    }

    @Test
    void userDeletionIsRestrictedWhileHistoricalMembershipExists() {
        MembershipFixture fixture = membershipFixture("user-delete-restricted");
        memberRepository.saveAndFlush(member(
                "member-user-delete-restricted",
                fixture.run(),
                fixture.community(),
                fixture.user()
        ));

        userRepository.delete(fixture.user());

        assertThatThrownBy(userRepository::flush)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingCommunityDoesNotDeleteRun() {
        MembershipFixture fixture = membershipFixture("community-delete");

        communityRepository.delete(fixture.community());
        communityRepository.flush();
        entityManager.clear();

        assertThat(communityRepository.findById(fixture.community().getId())).isEmpty();
        assertThat(runRepository.findById(fixture.run().getId())).isPresent();
        assertThat(userRepository.findById(fixture.user().getId())).isPresent();
    }

    private MembershipFixture membershipFixture(String suffix) {
        UserAccount user = userRepository.saveAndFlush(user("user-" + suffix));
        ClusteringRun run = runRepository.saveAndFlush(run("run-" + suffix, "version-" + suffix));
        Community community = communityRepository.saveAndFlush(
                community("community-" + suffix, run, 0)
        );
        return new MembershipFixture(run, community, user);
    }

    private static ClusteringRun run(String id, String version) {
        ClusteringRun run = new ClusteringRun();
        run.setId(id);
        run.setVersion(version);
        run.setAlgorithm(ClusteringAlgorithm.KMEANS);
        run.setClusterCount(2);
        run.setRandomState(42);
        run.setStatus(ClusteringRunStatus.PENDING);
        run.setActiveSlot(ClusteringRun.GLOBAL_ACTIVE_SLOT);
        run.setSampleCount(null);
        run.setFeatureSchemaVersion("community-features-v1");
        run.setParametersJson("{}");
        run.setCreatedBy("admin-test");
        return run;
    }

    private static ClusteringRun terminalRun(String id, String version) {
        ClusteringRun run = run(id, version);
        run.setStatus(ClusteringRunStatus.FAILED);
        run.setActiveSlot(null);
        run.setStartedAt(Instant.parse("2026-07-15T02:00:00Z"));
        run.setFinishedAt(Instant.parse("2026-07-15T03:00:00Z"));
        run.setErrorMessage("test failure");
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

    private static Activity activity(String id, String organizerId) {
        Activity activity = new Activity();
        activity.setId(id);
        activity.setTitle("级联保护测试活动");
        activity.setOrganizerId(organizerId);
        return activity;
    }

    private static Signup signup(String id, String activityId, String userId) {
        Signup signup = new Signup();
        signup.setId(id);
        signup.setActivityId(activityId);
        signup.setUserId(userId);
        return signup;
    }

    private static Favorite favorite(String id, String activityId, String userId) {
        Favorite favorite = new Favorite();
        favorite.setId(id);
        favorite.setActivityId(activityId);
        favorite.setUserId(userId);
        return favorite;
    }

    private static Feedback feedback(String id, String activityId, String userId) {
        Feedback feedback = new Feedback();
        feedback.setId(id);
        feedback.setActivityId(activityId);
        feedback.setUserId(userId);
        feedback.setUserName("测试用户");
        feedback.setRating(5);
        feedback.setContent("测试反馈");
        return feedback;
    }

    private static CheckInRecord checkIn(String id, String activityId, String userId) {
        CheckInRecord checkIn = new CheckInRecord();
        checkIn.setId(id);
        checkIn.setActivityId(activityId);
        checkIn.setUserId(userId);
        checkIn.setMethod("test");
        return checkIn;
    }

    private record MembershipFixture(ClusteringRun run, Community community, UserAccount user) {
    }
}
