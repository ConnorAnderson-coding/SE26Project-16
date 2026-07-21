package com.example.campusactivity.repository;

import com.example.campusactivity.entity.ClusteringRunInput;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClusteringRunInputRepository
        extends JpaRepository<ClusteringRunInput, String> {
    List<ClusteringRunInput> findByRunIdOrderBySampleOrderAsc(String runId);

    long countByRunId(String runId);
}
