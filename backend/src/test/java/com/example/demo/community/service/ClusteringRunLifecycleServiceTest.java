package com.example.demo.community.service;

import com.example.demo.community.client.ClusteringContracts.CommunitySummary;
import com.example.demo.community.client.ClusteringContracts.MemberResult;
import com.example.demo.community.client.ClusteringContracts.Metrics;
import com.example.demo.community.client.ClusteringContracts.Response;
import com.example.demo.entity.ClusteringRun;
import com.example.demo.entity.ClusteringRunInput;
import com.example.demo.entity.ClusteringRunStatus;
import com.example.demo.entity.User;
import com.example.demo.repository.ClusteringRunInputRepository;
import com.example.demo.repository.ClusteringRunRepository;
import com.example.demo.repository.CommunityMemberRepository;
import com.example.demo.repository.CommunityRepository;
import com.example.demo.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ClusteringRunLifecycleService.class, ClusteringRunLifecycleServiceTest.ClockConfig.class})
class ClusteringRunLifecycleServiceTest {

    @Autowired
    private ClusteringRunLifecycleService service;

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
    void transitionsPendingRunningSuccessAndPersistsResultAtomically() {
        userRepository.saveAndFlush(user("admin001"));
        User first = userRepository.saveAndFlush(user("lifecycle-u1"));
        User second = userRepository.saveAndFlush(user("lifecycle-u2"));
        ClusteringRun run = service.createPending(
                "admin001", 2, 2, 7, "community-features-v1",
                Map.of("clusterCount", 2), Map.of("schema", "community-features-v1")
        );
        inputRepository.saveAllAndFlush(List.of(input(run, first, 0), input(run, second, 1)));

        assertThat(service.claimNextPending()).contains(run.getId());
        service.complete(run.getId(), response(run));
        entityManager.clear();

        ClusteringRun completed = runRepository.findById(run.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(ClusteringRunStatus.SUCCESS);
        assertThat(completed.getActiveSlot()).isNull();
        assertThat(completed.getMetrics()).containsEntry("inertia", 1.25);
        assertThat(communityRepository.countByRunId(run.getId())).isEqualTo(2);
        assertThat(memberRepository.countByRunId(run.getId())).isEqualTo(2);
    }

    @Test
    void startupRecoveryMarksRunningRunFailedAndReleasesActiveSlot() {
        userRepository.saveAndFlush(user("admin001"));
        userRepository.saveAndFlush(user("recovery-user"));
        ClusteringRun run = service.createPending(
                "admin001", 2, 2, 7, "community-features-v1",
                Map.of(), Map.of("schema", "community-features-v1")
        );
        assertThat(service.claimNextPending()).contains(run.getId());

        assertThat(service.recoverInterruptedRuns()).isEqualTo(1);
        entityManager.clear();

        ClusteringRun recovered = runRepository.findById(run.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(ClusteringRunStatus.FAILED);
        assertThat(recovered.getActiveSlot()).isNull();
        assertThat(recovered.getErrorCode()).isEqualTo("EXECUTION_INTERRUPTED");
    }

    private Response response(ClusteringRun run) {
        return new Response(
                run.getId(), run.getVersion(), "KMEANS", 2, 2,
                new Metrics(1.25, List.of(0.75, 0.25)),
                List.of(
                        new CommunitySummary(0, 1, List.of("AI")),
                        new CommunitySummary(1, 1, List.of("体育"))
                ),
                List.of(
                        new MemberResult("lifecycle-u1", 0, 10.0, 20.0, 0.5),
                        new MemberResult("lifecycle-u2", 1, 90.0, 80.0, 0.6)
                )
        );
    }

    private static ClusteringRunInput input(ClusteringRun run, User user, int order) {
        ClusteringRunInput input = new ClusteringRunInput();
        input.setRun(run);
        input.setUser(user);
        input.setSampleOrder(order);
        input.setFeaturePayload(Map.of("userId", user.getId()));
        return input;
    }

    private static User user(String id) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 23, 10, 0);
        User user = new User();
        user.setId(id);
        user.setPasswordHash("test-password-hash");
        user.setName("聚类生命周期用户");
        user.setRole("student");
        user.setCollege("软件学院");
        user.setGrade("2024");
        user.setInterests(List.of("AI"));
        user.setAvailableTime(List.of("周末"));
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-07-23T10:00:00Z"), ZoneOffset.UTC);
        }
    }
}
