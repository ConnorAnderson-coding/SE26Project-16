package com.example.campusactivity.repository;

import com.example.campusactivity.entity.ClusteringRun;
import com.example.campusactivity.entity.ClusteringRunStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClusteringRunRepository extends JpaRepository<ClusteringRun, String> {
    Optional<ClusteringRun> findByVersion(String version);

    boolean existsByVersion(String version);

    boolean existsByStatusIn(Collection<ClusteringRunStatus> statuses);

    long countByStatusIn(Collection<ClusteringRunStatus> statuses);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ClusteringRun run
            SET run.status = com.example.campusactivity.entity.ClusteringRunStatus.RUNNING,
                run.activeSlot = 'GLOBAL',
                run.startedAt = :startedAt,
                run.finishedAt = NULL,
                run.errorMessage = NULL
            WHERE run.id = :runId
              AND run.status = com.example.campusactivity.entity.ClusteringRunStatus.PENDING
            """)
    int markRunningIfPending(
            @Param("runId") String runId,
            @Param("startedAt") Instant startedAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ClusteringRun run
            SET run.status = com.example.campusactivity.entity.ClusteringRunStatus.FAILED,
                run.activeSlot = NULL,
                run.finishedAt = :finishedAt,
                run.errorMessage = :errorMessage
            WHERE run.id = :runId
              AND run.status IN (
                  com.example.campusactivity.entity.ClusteringRunStatus.PENDING,
                  com.example.campusactivity.entity.ClusteringRunStatus.RUNNING
              )
            """)
    int markFailedIfActive(
            @Param("runId") String runId,
            @Param("finishedAt") Instant finishedAt,
            @Param("errorMessage") String errorMessage
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT run FROM ClusteringRun run WHERE run.id = :runId")
    Optional<ClusteringRun> findByIdForUpdate(@Param("runId") String runId);

    @Query("SELECT run.status FROM ClusteringRun run WHERE run.id = :runId")
    Optional<ClusteringRunStatus> findStatusById(@Param("runId") String runId);

    default Optional<ClusteringRun> findLatestSuccessful() {
        return findSuccessfulRunsForLatest(PageRequest.of(0, 1)).stream().findFirst();
    }

    @Query("""
            SELECT run
            FROM ClusteringRun run
            WHERE run.status = com.example.campusactivity.entity.ClusteringRunStatus.SUCCESS
              AND run.finishedAt IS NOT NULL
            ORDER BY run.finishedAt DESC, run.createdAt DESC, run.id DESC
            """)
    List<ClusteringRun> findSuccessfulRunsForLatest(Pageable pageable);
}
