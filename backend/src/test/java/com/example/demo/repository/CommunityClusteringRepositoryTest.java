package com.example.demo.repository;

import com.example.demo.entity.ClusteringAlgorithm;
import com.example.demo.entity.ClusteringRun;
import com.example.demo.entity.ClusteringRunInput;
import com.example.demo.entity.ClusteringRunStatus;
import com.example.demo.entity.Community;
import com.example.demo.entity.CommunityMember;
import com.example.demo.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommunityClusteringRepositoryTest {

    @Autowired
    private ClusteringRunRepository runRepository;

    @Autowired
    private ClusteringRunInputRepository inputRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityMemberRepository memberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsVersionedInputAndCompleteMembershipGraph() {
        User firstUser = userRepository.saveAndFlush(user("cluster-user-a"));
        User secondUser = userRepository.saveAndFlush(user("cluster-user-b"));
        ClusteringRun run = run("run-persist", "version-persist");
        run.setSampleCount(2);
        run.setFeatureDimension(7);
        run = runRepository.saveAndFlush(run);

        inputRepository.saveAndFlush(input(run, firstUser, 0, 3));
        inputRepository.saveAndFlush(input(run, secondUser, 1, 8));
        Community community = communityRepository.saveAndFlush(community(run, 0, 2));
        memberRepository.saveAndFlush(member(run, community, firstUser, 12.5));
        memberRepository.saveAndFlush(member(run, community, secondUser, 23.5));
        entityManager.clear();

        ClusteringRun savedRun = runRepository.findByVersion("version-persist").orElseThrow();
        assertThat(savedRun.getAlgorithm()).isEqualTo(ClusteringAlgorithm.KMEANS);
        assertThat(savedRun.getParameters()).containsEntry("windowDays", 180);
        assertThat(savedRun.getFeatureManifest()).containsEntry("schema", "community-features-v2");
        assertThat(inputRepository.findByRunIdOrderBySampleOrderAsc(savedRun.getId()))
                .extracting(input -> input.getUser().getId())
                .containsExactly("cluster-user-a", "cluster-user-b");
        assertThat(communityRepository.findByRunOrderByClusterNoAsc(savedRun))
                .singleElement()
                .extracting(Community::getTopInterests)
                .isEqualTo(List.of("编程", "AI"));
        assertThat(memberRepository.countByRunId(savedRun.getId())).isEqualTo(2);
    }

    @Test
    void uniqueActiveSlotRejectsSecondConcurrentRun() {
        runRepository.saveAndFlush(run("run-active-a", "version-active-a"));

        assertThatThrownBy(() -> runRepository.saveAndFlush(run("run-active-b", "version-active-b")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueRunUserRejectsMultipleCommunityMemberships() {
        User user = userRepository.saveAndFlush(user("cluster-user-unique"));
        ClusteringRun run = runRepository.saveAndFlush(run("run-unique", "version-unique"));
        Community first = communityRepository.saveAndFlush(community(run, 0, 1));
        Community second = communityRepository.saveAndFlush(community(run, 1, 1));
        memberRepository.saveAndFlush(member(run, first, user, 5.0));

        assertThatThrownBy(() -> memberRepository.saveAndFlush(member(run, second, user, 6.0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNonFiniteCoordinatesBeforePersistence() {
        User user = userRepository.saveAndFlush(user("cluster-user-nan"));
        ClusteringRun run = runRepository.saveAndFlush(run("run-nan", "version-nan"));
        Community community = communityRepository.saveAndFlush(community(run, 0, 1));
        CommunityMember member = member(run, community, user, Double.NaN);

        assertThatThrownBy(() -> memberRepository.saveAndFlush(member))
                .isInstanceOf(ConstraintViolationException.class);
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
        run.setFeatureSchemaVersion("community-features-v2");
        run.setParameters(Map.of("windowDays", 180));
        run.setFeatureManifest(Map.of("schema", "community-features-v2"));
        run.setCreatedBy("admin001");
        return run;
    }

    private static ClusteringRunInput input(ClusteringRun run, User user, int order, int signups) {
        ClusteringRunInput input = new ClusteringRunInput();
        input.setRun(run);
        input.setUser(user);
        input.setSampleOrder(order);
        input.setFeaturePayload(Map.of("userId", user.getId(), "signupCount", signups));
        return input;
    }

    private static Community community(ClusteringRun run, int clusterNo, int memberCount) {
        Community community = new Community();
        community.setRun(run);
        community.setClusterNo(clusterNo);
        community.setName("社区 " + (clusterNo + 1));
        community.setMemberCount(memberCount);
        community.setTopInterests(List.of("编程", "AI"));
        community.setColor("#1677FF");
        return community;
    }

    private static CommunityMember member(
            ClusteringRun run,
            Community community,
            User user,
            double coordinateX
    ) {
        CommunityMember member = new CommunityMember();
        member.setRun(run);
        member.setCommunity(community);
        member.setUser(user);
        member.setCoordinateX(coordinateX);
        member.setCoordinateY(50.0);
        member.setDistanceToCenter(0.5);
        return member;
    }

    private static User user(String id) {
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setId(id);
        user.setPasswordHash("test-password-hash");
        user.setName("聚类测试用户");
        user.setRole("student");
        user.setCollege("计算机学院");
        user.setGrade("2024");
        user.setInterests(List.of("编程"));
        user.setAvailableTime(List.of("周末"));
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }
}
