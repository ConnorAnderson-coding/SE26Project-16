package com.example.campusactivity.service.clustering;

import com.example.campusactivity.entity.ClusteringRun;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.repository.ClusteringRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class ClusteringRunFailureService {
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final ClusteringRunRepository runRepository;
    private final Clock clock;

    public ClusteringRunFailureService(ClusteringRunRepository runRepository, Clock clock) {
        this.runRepository = runRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FailureRecordingResult markFailed(
            String runId,
            ClusteringRunFailureCode failureCode
    ) {
        try {
            if (runId == null || runId.isBlank()) {
                return FailureRecordingResult.runNotFound();
            }
            if (failureCode == null) {
                fail(ClusteringRunStateCode.INVALID_INITIAL_PARAMETERS);
            }

            Optional<ClusteringRun> existing = runRepository.findById(runId);
            if (existing.isEmpty()) {
                return FailureRecordingResult.runNotFound();
            }
            ClusteringRun current = existing.get();
            if (current.getStatus() == ClusteringRunStatus.FAILED) {
                return FailureRecordingResult.of(
                        FailureRecordingResult.Outcome.ALREADY_FAILED,
                        ClusteringRunSnapshot.from(current)
                );
            }
            if (current.getStatus() == ClusteringRunStatus.SUCCESS) {
                return FailureRecordingResult.of(
                        FailureRecordingResult.Outcome.TERMINAL_SUCCESS_CONFLICT,
                        ClusteringRunSnapshot.from(current)
                );
            }
            if (current.getStatus() != ClusteringRunStatus.PENDING
                    && current.getStatus() != ClusteringRunStatus.RUNNING) {
                fail(ClusteringRunStateCode.INVALID_STATE_TRANSITION);
            }

            Instant finishedAt = clock.instant();
            validateFinishedAt(current, finishedAt);
            String errorMessage = failureCode.errorMessage();
            if (errorMessage.length() > MAX_ERROR_MESSAGE_LENGTH) {
                fail(ClusteringRunStateCode.RESULT_PERSISTENCE_FAILED);
            }

            int updated = runRepository.markFailedIfActive(runId, finishedAt, errorMessage);
            if (updated == 1) {
                ClusteringRun failed = runRepository.findById(runId)
                        .orElseThrow(() -> stateException(ClusteringRunStateCode.RUN_NOT_FOUND));
                return FailureRecordingResult.of(
                        FailureRecordingResult.Outcome.RECORDED,
                        ClusteringRunSnapshot.from(failed)
                );
            }
            return resolveConcurrentOutcome(runId);
        } catch (ClusteringRunStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            fail(ClusteringRunStateCode.RESULT_PERSISTENCE_FAILED);
            return null;
        }
    }

    private FailureRecordingResult resolveConcurrentOutcome(String runId) {
        Optional<ClusteringRun> current = runRepository.findById(runId);
        if (current.isEmpty()) {
            return FailureRecordingResult.runNotFound();
        }
        if (current.get().getStatus() == ClusteringRunStatus.FAILED) {
            return FailureRecordingResult.of(
                    FailureRecordingResult.Outcome.ALREADY_FAILED,
                    ClusteringRunSnapshot.from(current.get())
            );
        }
        if (current.get().getStatus() == ClusteringRunStatus.SUCCESS) {
            return FailureRecordingResult.of(
                    FailureRecordingResult.Outcome.TERMINAL_SUCCESS_CONFLICT,
                    ClusteringRunSnapshot.from(current.get())
            );
        }
        fail(ClusteringRunStateCode.RESULT_PERSISTENCE_FAILED);
        return null;
    }

    private static void validateFinishedAt(ClusteringRun run, Instant finishedAt) {
        if (run.getCreatedAt() == null || finishedAt.isBefore(run.getCreatedAt())) {
            fail(ClusteringRunStateCode.INVALID_STATE_TRANSITION);
        }
        if (run.getStartedAt() != null && finishedAt.isBefore(run.getStartedAt())) {
            fail(ClusteringRunStateCode.INVALID_STATE_TRANSITION);
        }
    }

    private static ClusteringRunStateException stateException(ClusteringRunStateCode code) {
        return new ClusteringRunStateException(code);
    }

    private static void fail(ClusteringRunStateCode code) {
        throw stateException(code);
    }
}
