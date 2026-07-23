package com.example.demo.community.service;

import com.example.demo.community.client.ClusteringContracts.FeatureSample;
import com.example.demo.entity.ClusteringRun;
import com.example.demo.entity.ClusteringRunInput;
import com.example.demo.repository.ClusteringRunInputRepository;
import com.example.demo.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClusteringSubmissionService {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final CommunityFeatureBuilder featureBuilder;
    private final ClusteringRunLifecycleService lifecycleService;
    private final ClusteringRunInputRepository inputRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ClusteringSubmissionService(
            CommunityFeatureBuilder featureBuilder,
            ClusteringRunLifecycleService lifecycleService,
            ClusteringRunInputRepository inputRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this.featureBuilder = featureBuilder;
        this.lifecycleService = lifecycleService;
        this.inputRepository = inputRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ClusteringRun submit(String createdBy, int clusterCount) {
        CommunityFeatureSnapshot snapshot = featureBuilder.build();
        if (snapshot.samples().size() < 2 || clusterCount < 2 || clusterCount > snapshot.samples().size()) {
            throw new ClusteringStateException(ClusteringStateException.Code.INVALID_PARAMETERS);
        }
        Map<String, Object> parameters = Map.of(
                "clusterCount", clusterCount,
                "randomState", 42,
                "windowDays", CommunityFeatureBuilder.WINDOW_DAYS
        );
        ClusteringRun run = lifecycleService.createPending(
                createdBy,
                clusterCount,
                snapshot.samples().size(),
                snapshot.featureDimension(),
                snapshot.schemaVersion(),
                parameters,
                snapshot.manifest()
        );

        List<ClusteringRunInput> inputs = new ArrayList<>(snapshot.samples().size());
        for (int index = 0; index < snapshot.samples().size(); index++) {
            FeatureSample sample = snapshot.samples().get(index);
            ClusteringRunInput input = new ClusteringRunInput();
            input.setRun(run);
            input.setUser(userRepository.getReferenceById(sample.userId()));
            input.setSampleOrder(index);
            input.setFeaturePayload(objectMapper.convertValue(sample, MAP_TYPE));
            inputs.add(input);
        }
        inputRepository.saveAllAndFlush(inputs);
        return run;
    }
}
