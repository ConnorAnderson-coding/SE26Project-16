package com.example.campusactivity.repository;

import com.example.campusactivity.entity.ClusteringRun;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ClusteringRunRepository extends JpaRepository<ClusteringRun, String> {
    Optional<ClusteringRun> findByVersion(String version);

    boolean existsByVersion(String version);

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
