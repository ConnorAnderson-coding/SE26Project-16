package com.example.campusactivity.repository;

import com.example.campusactivity.entity.ClusteringRun;
import com.example.campusactivity.entity.Community;
import com.example.campusactivity.repository.projection.CommunityQueryProjection;
import com.example.campusactivity.repository.projection.AdminCommunitySummaryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommunityRepository extends JpaRepository<Community, String> {
    List<Community> findByRunOrderByClusterNoAsc(ClusteringRun run);

    Optional<Community> findByRunAndClusterNo(ClusteringRun run, Integer clusterNo);

    @Query("""
            SELECT CASE WHEN COUNT(community) > 0 THEN true ELSE false END
            FROM Community community
            WHERE community.run.id = :runId
            """)
    boolean existsByRunId(@Param("runId") String runId);

    @Query("SELECT COUNT(community) FROM Community community WHERE community.run.id = :runId")
    long countByRunId(@Param("runId") String runId);

    @Query("""
            SELECT community.id AS communityId,
                   community.clusterNo AS clusterNo,
                   community.name AS name,
                   community.description AS description,
                   community.memberCount AS memberCount,
                   community.topInterestsJson AS topInterestsJson,
                   community.color AS color
            FROM Community community
            WHERE community.run.id = :runId
            ORDER BY community.clusterNo ASC, community.id ASC
            """)
    List<CommunityQueryProjection> findQueryProjectionsByRunId(
            @Param("runId") String runId
    );

    @Query("""
            SELECT community.id AS communityId,
                   community.run.id AS runId,
                   community.clusterNo AS clusterNo,
                   community.name AS name,
                   community.color AS color,
                   community.memberCount AS memberCount
            FROM Community community
            WHERE community.id = :communityId
            """)
    Optional<AdminCommunitySummaryProjection> findAdminSummaryById(
            @Param("communityId") String communityId
    );
}
