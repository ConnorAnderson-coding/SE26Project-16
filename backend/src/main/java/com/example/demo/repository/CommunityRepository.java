package com.example.demo.repository;

import com.example.demo.entity.ClusteringRun;
import com.example.demo.entity.Community;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunityRepository extends JpaRepository<Community, String> {

    List<Community> findByRunOrderByClusterNoAsc(ClusteringRun run);

    Optional<Community> findByRunAndClusterNo(ClusteringRun run, Integer clusterNo);

    boolean existsByRunId(String runId);

    long countByRunId(String runId);
}
