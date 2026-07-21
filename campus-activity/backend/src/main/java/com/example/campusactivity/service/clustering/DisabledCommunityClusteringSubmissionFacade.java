package com.example.campusactivity.service.clustering;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        prefix = "community-clustering.python",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
final class DisabledCommunityClusteringSubmissionFacade
        implements CommunityClusteringSubmissionFacade {
    @Override
    public ClusteringSubmissionResult submit(int clusterCount, String createdBy) {
        throw new ClusteringServiceDisabledException();
    }
}
