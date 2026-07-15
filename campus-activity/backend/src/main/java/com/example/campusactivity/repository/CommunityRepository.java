package com.example.campusactivity.repository;

import com.example.campusactivity.entity.ClusteringRun;
import com.example.campusactivity.entity.Community;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunityRepository extends JpaRepository<Community, String> {
    List<Community> findByRunOrderByClusterNoAsc(ClusteringRun run);

    Optional<Community> findByRunAndClusterNo(ClusteringRun run, Integer clusterNo);
}
