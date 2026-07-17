package com.example.campusactivity.service.clustering;

import java.util.Objects;
import java.util.Optional;

public record FailureRecordingResult(
        Outcome outcome,
        Optional<ClusteringRunSnapshot> run
) {
    public FailureRecordingResult {
        Objects.requireNonNull(outcome, "outcome");
        run = Objects.requireNonNull(run, "run");
    }

    static FailureRecordingResult of(Outcome outcome, ClusteringRunSnapshot run) {
        return new FailureRecordingResult(outcome, Optional.of(run));
    }

    static FailureRecordingResult runNotFound() {
        return new FailureRecordingResult(Outcome.RUN_NOT_FOUND, Optional.empty());
    }

    public enum Outcome {
        RECORDED,
        ALREADY_FAILED,
        RUN_NOT_FOUND,
        TERMINAL_SUCCESS_CONFLICT
    }
}
