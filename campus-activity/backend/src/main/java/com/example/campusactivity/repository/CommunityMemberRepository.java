package com.example.campusactivity.repository;

import com.example.campusactivity.entity.ClusteringRun;
import com.example.campusactivity.entity.Community;
import com.example.campusactivity.entity.CommunityMember;
import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.projection.CommunityMemberPointProjection;
import com.example.campusactivity.repository.projection.CurrentUserMembershipProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, String> {
    List<CommunityMember> findByRun(ClusteringRun run);

    List<CommunityMember> findByCommunity(Community community);

    Optional<CommunityMember> findByRunAndUser(ClusteringRun run, UserAccount user);

    boolean existsByRunAndUser(ClusteringRun run, UserAccount user);

    boolean existsByRunId(String runId);

    long countByRunId(String runId);

    long countByCommunityId(String communityId);

    @Query("""
            SELECT member.id AS pointId,
                   community.id AS communityId,
                   community.clusterNo AS clusterNo,
                   member.coordinateX AS coordinateX,
                   member.coordinateY AS coordinateY,
                   CASE WHEN member.user.id = :currentUserId THEN true ELSE false END AS currentUser
            FROM CommunityMember member
            JOIN member.community community
            WHERE member.run.id = :runId
            ORDER BY community.clusterNo ASC, member.id ASC
            """)
    List<CommunityMemberPointProjection> findPointProjectionsByRunId(
            @Param("runId") String runId,
            @Param("currentUserId") String currentUserId
    );

    @Query("""
            SELECT member.id AS pointId,
                   community.id AS communityId,
                   community.clusterNo AS clusterNo,
                   community.name AS communityName,
                   community.color AS color,
                   member.coordinateX AS coordinateX,
                   member.coordinateY AS coordinateY,
                   member.distanceToCenter AS distanceToCenter
            FROM CommunityMember member
            JOIN member.community community
            WHERE member.run.id = :runId
              AND member.user.id = :currentUserId
            """)
    Optional<CurrentUserMembershipProjection> findMembershipProjection(
            @Param("runId") String runId,
            @Param("currentUserId") String currentUserId
    );
}
