package com.example.campusactivity.repository;

import com.example.campusactivity.entity.ClusteringRun;
import com.example.campusactivity.entity.Community;
import com.example.campusactivity.entity.CommunityMember;
import com.example.campusactivity.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, String> {
    List<CommunityMember> findByRun(ClusteringRun run);

    List<CommunityMember> findByCommunity(Community community);

    Optional<CommunityMember> findByRunAndUser(ClusteringRun run, UserAccount user);

    boolean existsByRunAndUser(ClusteringRun run, UserAccount user);
}
