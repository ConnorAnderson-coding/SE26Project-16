package com.example.campusactivity.service.clustering;

import com.example.campusactivity.client.clustering.dto.FeatureSample;
import com.example.campusactivity.entity.ClusteringRun;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class ClusteringSubmissionPersistenceService {
    private final ClusteringRunLifecycleService lifecycleService;
    private final ClusteringRunInputCodec inputCodec;
    private final EntityManager entityManager;

    ClusteringSubmissionPersistenceService(
            ClusteringRunLifecycleService lifecycleService,
            ClusteringRunInputCodec inputCodec,
            EntityManager entityManager
    ) {
        this.lifecycleService = lifecycleService;
        this.inputCodec = inputCodec;
        this.entityManager = entityManager;
    }

    @Transactional
    ClusteringSubmissionResult persist(
            int clusterCount,
            String createdBy,
            List<FeatureSample> samples
    ) {
        ClusteringRunSnapshot pending = lifecycleService.createPending(
                new ClusteringRunCreationCommand(clusterCount, samples.size(), createdBy)
        );
        ClusteringRun run = entityManager.getReference(
                ClusteringRun.class,
                pending.runId()
        );
        for (int index = 0; index < samples.size(); index++) {
            entityManager.persist(inputCodec.encode(run, index, samples.get(index)));
        }
        entityManager.flush();
        return ClusteringSubmissionResult.from(pending);
    }
}
