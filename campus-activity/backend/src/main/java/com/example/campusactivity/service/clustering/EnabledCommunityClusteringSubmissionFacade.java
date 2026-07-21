package com.example.campusactivity.service.clustering;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        prefix = "community-clustering.python",
        name = "enabled",
        havingValue = "true"
)
final class EnabledCommunityClusteringSubmissionFacade
        implements CommunityClusteringSubmissionFacade {
    private final CommunityClusteringSubmissionService submissionService;

    EnabledCommunityClusteringSubmissionFacade(
            CommunityClusteringSubmissionService submissionService
    ) {
        this.submissionService = submissionService;
    }

    @Override
    public ClusteringSubmissionResult submit(int clusterCount, String createdBy) {
        return submissionService.submit(clusterCount, createdBy);
    }
}
