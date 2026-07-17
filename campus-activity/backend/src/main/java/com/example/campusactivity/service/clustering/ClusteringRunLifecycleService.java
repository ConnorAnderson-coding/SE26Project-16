package com.example.campusactivity.service.clustering;

import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRun;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.repository.ClusteringRunRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;

@Service
public class ClusteringRunLifecycleService {
    static final int RANDOM_STATE = 42;
    static final String FEATURE_SCHEMA_VERSION = "community-features-v1";

    private static final int MIN_CLUSTER_COUNT = 2;
    private static final int MAX_CREATED_BY_LENGTH = 255;

    private final ClusteringRunRepository runRepository;
    private final ClusteringIdentifierGenerator identifierGenerator;
    private final ClusteringPersistenceJsonSerializer jsonSerializer;
    private final Clock clock;
    private final EntityManager entityManager;

    public ClusteringRunLifecycleService(
            ClusteringRunRepository runRepository,
            ClusteringIdentifierGenerator identifierGenerator,
            ClusteringPersistenceJsonSerializer jsonSerializer,
            Clock clock,
            EntityManager entityManager
    ) {
        this.runRepository = runRepository;
        this.identifierGenerator = identifierGenerator;
        this.jsonSerializer = jsonSerializer;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Transactional
    public ClusteringRunSnapshot createPending(ClusteringRunCreationCommand command) {
        try {
            validateCreationCommand(command);
            if (runRepository.existsByStatusIn(
                    EnumSet.of(ClusteringRunStatus.PENDING, ClusteringRunStatus.RUNNING)
            )) {
                fail(ClusteringRunStateCode.ACTIVE_RUN_EXISTS);
            }

            Instant createdAt = clock.instant();
            ClusteringRun run = new ClusteringRun();
            run.setId(identifierGenerator.newRunId());
            run.setVersion(identifierGenerator.newVersion());
            run.setAlgorithm(ClusteringAlgorithm.KMEANS);
            run.setClusterCount(command.clusterCount());
            run.setRandomState(RANDOM_STATE);
            run.setStatus(ClusteringRunStatus.PENDING);
            run.setActiveSlot(ClusteringRun.GLOBAL_ACTIVE_SLOT);
            run.setSampleCount(command.sampleCount());
            run.setFeatureSchemaVersion(FEATURE_SCHEMA_VERSION);
            run.setParametersJson(jsonSerializer.parametersJson(command.clusterCount()));
            run.setMetricsJson(null);
            run.setStartedAt(null);
            run.setFinishedAt(null);
            run.setErrorMessage(null);
            run.setCreatedBy(command.createdBy());
            run.setCreatedAt(createdAt);

            validateGeneratedIdentifier(run.getId());
            validateGeneratedIdentifier(run.getVersion());
            persistNewRun(run);
            return ClusteringRunSnapshot.from(run);
        } catch (ClusteringRunStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            fail(ClusteringRunStateCode.RESULT_PERSISTENCE_FAILED);
            return null;
        }
    }

    private void persistNewRun(ClusteringRun run) {
        try {
            entityManager.persist(run);
            entityManager.flush();
        } catch (RuntimeException exception) {
            fail(ClusteringRunStateCode.RUN_CREATION_CONFLICT);
        }
    }

    @Transactional
    public ClusteringRunSnapshot markRunning(String runId) {
        try {
            validateRunId(runId);
            ClusteringRun current = runRepository.findById(runId)
                    .orElseThrow(() -> stateException(ClusteringRunStateCode.RUN_NOT_FOUND));
            requirePending(current.getStatus());

            Instant startedAt = clock.instant();
            if (current.getCreatedAt() == null || startedAt.isBefore(current.getCreatedAt())) {
                fail(ClusteringRunStateCode.INVALID_STATE_TRANSITION);
            }

            int updated = runRepository.markRunningIfPending(runId, startedAt);
            if (updated != 1) {
                throwTransitionConflict(runId);
            }
            ClusteringRun updatedRun = runRepository.findById(runId)
                    .orElseThrow(() -> stateException(ClusteringRunStateCode.RUN_NOT_FOUND));
            return ClusteringRunSnapshot.from(updatedRun);
        } catch (ClusteringRunStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            fail(ClusteringRunStateCode.RESULT_PERSISTENCE_FAILED);
            return null;
        }
    }

    private static void validateCreationCommand(ClusteringRunCreationCommand command) {
        if (command == null
                || command.clusterCount() == null
                || command.clusterCount() < MIN_CLUSTER_COUNT
                || command.sampleCount() == null
                || command.sampleCount() < 0
                || command.clusterCount() > command.sampleCount()
                || command.createdBy() == null
                || command.createdBy().isBlank()
                || command.createdBy().length() > MAX_CREATED_BY_LENGTH) {
            fail(ClusteringRunStateCode.INVALID_INITIAL_PARAMETERS);
        }
    }

    private static void validateRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            fail(ClusteringRunStateCode.RUN_NOT_FOUND);
        }
    }

    private static void validateGeneratedIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank() || identifier.length() > 64) {
            fail(ClusteringRunStateCode.RESULT_PERSISTENCE_FAILED);
        }
    }

    private static void requirePending(ClusteringRunStatus status) {
        if (status == ClusteringRunStatus.SUCCESS || status == ClusteringRunStatus.FAILED) {
            fail(ClusteringRunStateCode.RUN_ALREADY_TERMINAL);
        }
        if (status != ClusteringRunStatus.PENDING) {
            fail(ClusteringRunStateCode.INVALID_STATE_TRANSITION);
        }
    }

    private void throwTransitionConflict(String runId) {
        ClusteringRunStatus status = runRepository.findStatusById(runId)
                .orElseThrow(() -> stateException(ClusteringRunStateCode.RUN_NOT_FOUND));
        requirePending(status);
        fail(ClusteringRunStateCode.INVALID_STATE_TRANSITION);
    }

    private static ClusteringRunStateException stateException(ClusteringRunStateCode code) {
        return new ClusteringRunStateException(code);
    }

    private static void fail(ClusteringRunStateCode code) {
        throw stateException(code);
    }
}
