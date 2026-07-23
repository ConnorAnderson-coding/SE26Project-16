package com.example.demo.community.service;

import com.example.demo.community.client.ClusteringContracts;
import com.example.demo.community.client.ClusteringContracts.FeatureSample;
import com.example.demo.community.client.ClusteringContracts.Request;
import com.example.demo.entity.ClusteringRun;
import com.example.demo.entity.ClusteringRunInput;
import com.example.demo.repository.ClusteringRunInputRepository;
import com.example.demo.repository.ClusteringRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DatabaseClusteringRequestFactory implements ClusteringRequestFactory {

    private final ClusteringRunRepository runRepository;
    private final ClusteringRunInputRepository inputRepository;
    private final ObjectMapper objectMapper;

    public DatabaseClusteringRequestFactory(
            ClusteringRunRepository runRepository,
            ClusteringRunInputRepository inputRepository,
            ObjectMapper objectMapper
    ) {
        this.runRepository = runRepository;
        this.inputRepository = inputRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Request buildRequest(String runId) {
        ClusteringRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ClusteringStateException(ClusteringStateException.Code.RUN_NOT_FOUND));
        List<ClusteringRunInput> inputs = inputRepository.findByRunIdOrderBySampleOrderAsc(runId);
        if (run.getSampleCount() == null || inputs.size() != run.getSampleCount()) {
            throw new ClusteringStateException(ClusteringStateException.Code.INVALID_PARAMETERS);
        }
        List<FeatureSample> samples = inputs.stream()
                .map(input -> objectMapper.convertValue(input.getFeaturePayload(), FeatureSample.class))
                .toList();
        return new Request(
                run.getId(),
                run.getVersion(),
                ClusteringContracts.ALGORITHM,
                run.getClusterCount(),
                ClusteringContracts.RANDOM_STATE,
                run.getFeatureSchemaVersion(),
                samples
        );
    }
}
