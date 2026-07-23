package com.example.demo.repository;

import com.example.demo.entity.ClusteringRun;
import com.example.demo.entity.Community;
import com.example.demo.entity.CommunityMember;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, String> {

    List<CommunityMember> findByRun(ClusteringRun run);

    List<CommunityMember> findByCommunity(Community community);

    Optional<CommunityMember> findByRunAndUser(ClusteringRun run, User user);

    boolean existsByRunAndUser(ClusteringRun run, User user);

    boolean existsByRunId(String runId);

    long countByRunId(String runId);

    long countByCommunityId(String communityId);
}
