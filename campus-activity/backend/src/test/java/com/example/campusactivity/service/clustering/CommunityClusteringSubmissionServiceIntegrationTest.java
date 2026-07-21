package com.example.campusactivity.service.clustering;

import com.example.campusactivity.client.clustering.dto.FeatureSample;
import com.example.campusactivity.entity.ClusteringRunInput;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.ClusteringRunInputRepository;
import com.example.campusactivity.repository.ClusteringRunRepository;
import com.example.campusactivity.repository.UserRepository;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "community-clustering.python.enabled=false")
class CommunityClusteringSubmissionServiceIntegrationTest {
    private static final String MUTABLE_USER_ID = "snapshot-live-user";

    @Autowired
    private CommunityClusteringSubmissionService submissionService;

    @Autowired
    private ClusteringRunInputRepository inputRepository;

    @Autowired
    private ClusteringRunRepository runRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClusteringRunInputCodec inputCodec;

    @MockBean
    private CommunityFeatureAggregationService aggregationService;

    @MockBean
    private com.example.campusactivity.client.clustering.ClusteringClient clusteringClient;

    @AfterEach
    void cleanDatabase() {
        inputRepository.deleteAll();
        runRepository.deleteAll();
        userRepository.deleteAllById(List.of(MUTABLE_USER_ID));
    }

    @Test
    void atomicallyPersistsPendingRunAndAllFrozenInputs() {
        List<FeatureSample> samples = List.of(sample("student-2"), sample("student-1"));
        when(aggregationService.aggregateFeatureSamples()).thenReturn(result(samples));

        ClusteringSubmissionResult submitted = submissionService.submit(2, "admin-1");

        assertThat(submitted.status()).isEqualTo(ClusteringRunStatus.PENDING);
        assertThat(submitted.clusterCount()).isEqualTo(2);
        assertThat(submitted.randomState()).isEqualTo(42);
        assertThat(submitted.createdAt()).isNotNull();
        assertThat(runRepository.findById(submitted.runId()))
                .get()
                .extracting(run -> run.getSampleCount())
                .isEqualTo(2);

        List<ClusteringRunInput> inputs = inputRepository
                .findByRunIdOrderBySampleOrderAsc(submitted.runId());
        assertThat(inputs).extracting(ClusteringRunInput::getUserId)
                .containsExactly("student-2", "student-1");
        assertThat(inputs).extracting(inputCodec::decode).containsExactlyElementsOf(samples);
        verify(clusteringClient, never()).runClustering(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anyInputPersistenceFailureRollsBackRunAndEveryInput() {
        FeatureSample invalidForStorage = sample(
                "student-2",
                "x".repeat(256)
        );
        when(aggregationService.aggregateFeatureSamples()).thenReturn(result(List.of(
                sample("student-1"),
                invalidForStorage
        )));

        assertThatThrownBy(() -> submissionService.submit(2, "admin-1"))
                .isInstanceOf(ClusteringSubmissionException.class)
                .extracting(exception -> ((ClusteringSubmissionException) exception).getCode())
                .isEqualTo(ClusteringRunFailureCode.INTERNAL_ERROR);
        assertThat(runRepository.count()).isZero();
        assertThat(inputRepository.count()).isZero();
    }

    @Test
    void rejectsNoEffectiveUsersAndInvalidClusterCountsBeforePersistence() {
        when(aggregationService.aggregateFeatureSamples()).thenReturn(result(List.of()));
        assertFailure(2, ClusteringRunFailureCode.NO_EFFECTIVE_USERS);

        assertThatThrownBy(() -> submissionService.submit(1, "admin-1"))
                .isInstanceOf(ClusteringSubmissionException.class)
                .extracting(exception -> ((ClusteringSubmissionException) exception).getCode())
                .isEqualTo(ClusteringRunFailureCode.INVALID_CLUSTER_COUNT);

        when(aggregationService.aggregateFeatureSamples()).thenReturn(result(List.of(
                sample("student-1"),
                sample("student-2")
        )));
        assertFailure(3, ClusteringRunFailureCode.INVALID_CLUSTER_COUNT);
        assertThat(runRepository.count()).isZero();
        assertThat(inputRepository.count()).isZero();
    }

    @Test
    void activeRunPrecheckIsReportedAsStableActiveRunExists() {
        when(aggregationService.aggregateFeatureSamples()).thenReturn(result(List.of(
                sample("student-1"),
                sample("student-2")
        )));
        submissionService.submit(2, "admin-1");

        assertFailure(2, ClusteringRunFailureCode.ACTIVE_RUN_EXISTS);
        assertThat(runRepository.count()).isEqualTo(1);
        assertThat(inputRepository.count()).isEqualTo(2);
    }

    @Test
    void concurrentSubmissionsCreateOneRunAndLoserIsStableConflict() throws Exception {
        when(aggregationService.aggregateFeatureSamples()).thenReturn(result(List.of(
                sample("student-1"),
                sample("student-2")
        )));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = threads.submit(() -> submitAfter(start, "admin-1"));
            Future<Object> second = threads.submit(() -> submitAfter(start, "admin-2"));
            start.countDown();

            List<Object> outcomes = List.of(first.get(), second.get());
            assertThat(outcomes).filteredOn(ClusteringSubmissionResult.class::isInstance)
                    .hasSize(1);
            assertThat(outcomes).filteredOn(ClusteringSubmissionException.class::isInstance)
                    .singleElement()
                    .extracting(value -> ((ClusteringSubmissionException) value).getCode())
                    .isEqualTo(ClusteringRunFailureCode.ACTIVE_RUN_EXISTS);
            assertThat(runRepository.count()).isEqualTo(1);
            assertThat(inputRepository.count()).isEqualTo(2);
        } finally {
            threads.shutdownNow();
        }
    }

    @Test
    void storedSnapshotDoesNotChangeWhenSourceCollectionsChange() {
        List<String> interests = new ArrayList<>(List.of("AI"));
        FeatureSample sample = new FeatureSample(
                "student-1",
                interests,
                "Computer Science",
                "2026",
                List.of("MONDAY"),
                1,
                1,
                0,
                0,
                0,
                null,
                Map.of("Technology", 1)
        );
        interests.add("Music");
        when(aggregationService.aggregateFeatureSamples()).thenReturn(result(List.of(
                sample,
                sample("student-2")
        )));

        String runId = submissionService.submit(2, "admin-1").runId();

        FeatureSample restored = inputCodec.decode(inputRepository
                .findByRunIdOrderBySampleOrderAsc(runId)
                .get(0));
        assertThat(restored.interests()).containsExactly("AI");
    }

    @Test
    void storedCollegeAndGradeDoNotChangeWithLiveUserProfile() {
        UserAccount liveUser = new UserAccount();
        liveUser.setId(MUTABLE_USER_ID);
        liveUser.setPassword("not-used-by-clustering");
        liveUser.setName("Snapshot User");
        liveUser.setCollege("Original College");
        liveUser.setGrade("2026");
        userRepository.saveAndFlush(liveUser);
        FeatureSample frozen = sample(
                MUTABLE_USER_ID,
                liveUser.getCollege(),
                liveUser.getGrade()
        );
        when(aggregationService.aggregateFeatureSamples()).thenReturn(result(List.of(
                frozen,
                sample("student-2")
        )));

        String runId = submissionService.submit(2, "admin-1").runId();
        liveUser.setCollege("Changed College");
        liveUser.setGrade("2030");
        userRepository.saveAndFlush(liveUser);

        FeatureSample restored = inputCodec.decode(inputRepository
                .findByRunIdOrderBySampleOrderAsc(runId)
                .get(0));
        assertThat(restored.college()).isEqualTo("Original College");
        assertThat(restored.grade()).isEqualTo("2026");
    }

    @Test
    void inputEntityContainsNoUserProfileAssociationOrIdentityFields() {
        Field[] declaredFields = ClusteringRunInput.class.getDeclaredFields();
        Set<String> fields = java.util.Arrays.stream(declaredFields)
                .map(Field::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(fields).doesNotContain(
                "user",
                "userAccount",
                "name",
                "password",
                "role",
                "friends"
        );
        List<Class<?>> associationTypes = java.util.Arrays.stream(declaredFields)
                .filter(field -> field.isAnnotationPresent(ManyToOne.class)
                        || field.isAnnotationPresent(OneToOne.class))
                .map(Field::getType)
                .toList();
        assertThat(associationTypes)
                .containsExactly(com.example.campusactivity.entity.ClusteringRun.class)
                .doesNotContain(UserAccount.class);
    }

    private void assertFailure(int clusterCount, ClusteringRunFailureCode expected) {
        assertThatThrownBy(() -> submissionService.submit(clusterCount, "admin-1"))
                .isInstanceOf(ClusteringSubmissionException.class)
                .extracting(exception -> ((ClusteringSubmissionException) exception).getCode())
                .isEqualTo(expected);
    }

    private Object submitAfter(CountDownLatch start, String createdBy) {
        try {
            start.await();
            return submissionService.submit(2, createdBy);
        } catch (ClusteringSubmissionException exception) {
            return exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static FeatureAggregationResult result(List<FeatureSample> samples) {
        return new FeatureAggregationResult(
                samples,
                List.of(),
                new FeatureAggregationDiagnostics(0, 0, 0, 0, 0, 0, 0, 0)
        );
    }

    private static FeatureSample sample(String userId) {
        return sample(userId, "Computer Science");
    }

    private static FeatureSample sample(String userId, String college) {
        return sample(userId, college, "2026");
    }

    private static FeatureSample sample(String userId, String college, String grade) {
        return new FeatureSample(
                userId,
                List.of("AI"),
                college,
                grade,
                List.of("MONDAY"),
                1,
                1,
                0,
                0,
                0,
                null,
                Map.of("Technology", 1)
        );
    }
}
