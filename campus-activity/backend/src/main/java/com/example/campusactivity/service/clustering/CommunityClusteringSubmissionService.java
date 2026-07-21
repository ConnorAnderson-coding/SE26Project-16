package com.example.campusactivity.service.clustering;

import com.example.campusactivity.client.clustering.dto.FeatureSample;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
public class CommunityClusteringSubmissionService {
    private final CommunityFeatureAggregationService featureAggregationService;
    private final ClusteringSubmissionPersistenceService persistenceService;

    public CommunityClusteringSubmissionService(
            CommunityFeatureAggregationService featureAggregationService,
            ClusteringSubmissionPersistenceService persistenceService
    ) {
        this.featureAggregationService = featureAggregationService;
        this.persistenceService = persistenceService;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NEVER)
    public ClusteringSubmissionResult submit(int clusterCount, String createdBy) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw failure(ClusteringRunFailureCode.INTERNAL_ERROR, 0, clusterCount);
        }
        if (clusterCount < 2) {
            throw failure(ClusteringRunFailureCode.INVALID_CLUSTER_COUNT, 0, clusterCount);
        }

        List<FeatureSample> samples;
        try {
            samples = List.copyOf(featureAggregationService.aggregateFeatureSamples().samples());
        } catch (RuntimeException exception) {
            throw failure(ClusteringRunFailureCode.FEATURE_AGGREGATION_FAILED, 0, clusterCount);
        }

        int sampleCount = samples.size();
        if (sampleCount == 0) {
            throw failure(ClusteringRunFailureCode.NO_EFFECTIVE_USERS, 0, clusterCount);
        }
        if (clusterCount > sampleCount) {
            throw failure(
                    ClusteringRunFailureCode.INVALID_CLUSTER_COUNT,
                    sampleCount,
                    clusterCount
            );
        }

        try {
            return persistenceService.persist(clusterCount, createdBy, samples);
        } catch (ClusteringRunStateException exception) {
            ClusteringRunFailureCode code = switch (exception.getCode()) {
                case ACTIVE_RUN_EXISTS, RUN_CREATION_CONFLICT ->
                        ClusteringRunFailureCode.ACTIVE_RUN_EXISTS;
                default -> ClusteringRunFailureCode.INTERNAL_ERROR;
            };
            throw failure(code, sampleCount, clusterCount);
        } catch (RuntimeException exception) {
            throw failure(
                    ClusteringRunFailureCode.INTERNAL_ERROR,
                    sampleCount,
                    clusterCount
            );
        }
    }

    private static ClusteringSubmissionException failure(
            ClusteringRunFailureCode code,
            int sampleCount,
            int clusterCount
    ) {
        return new ClusteringSubmissionException(code, sampleCount, clusterCount);
    }
}
