package com.example.demo.repository;

import com.example.demo.entity.ClusteringRun;
import com.example.demo.entity.ClusteringRunStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClusteringRunRepository extends JpaRepository<ClusteringRun, String> {

    Optional<ClusteringRun> findByVersion(String version);

    boolean existsByVersion(String version);

    boolean existsByStatusIn(Collection<ClusteringRunStatus> statuses);

    @Query("""
            SELECT run.id
            FROM ClusteringRun run
            WHERE run.status = :status
            ORDER BY run.createdAt ASC, run.id ASC
            """)
    List<String> findIdsByStatusOrderByCreatedAtAscIdAsc(@Param("status") ClusteringRunStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT run
            FROM ClusteringRun run
            WHERE run.status = com.example.demo.entity.ClusteringRunStatus.PENDING
            ORDER BY run.createdAt ASC, run.id ASC
            """)
    List<ClusteringRun> findPendingForClaim(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT run FROM ClusteringRun run WHERE run.id = :runId")
    Optional<ClusteringRun> findByIdForUpdate(@Param("runId") String runId);

    default Optional<ClusteringRun> findLatestSuccessful() {
        return findSuccessfulForLatest(Pageable.ofSize(1)).stream().findFirst();
    }

    @Query("""
            SELECT run
            FROM ClusteringRun run
            WHERE run.status = com.example.demo.entity.ClusteringRunStatus.SUCCESS
              AND run.finishedAt IS NOT NULL
            ORDER BY run.finishedAt DESC, run.createdAt DESC, run.id DESC
            """)
    List<ClusteringRun> findSuccessfulForLatest(Pageable pageable);
}
