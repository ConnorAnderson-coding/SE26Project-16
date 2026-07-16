package com.example.campusactivity.client.clustering;

import com.example.campusactivity.client.clustering.dto.ClusteringRequest;
import com.example.campusactivity.client.clustering.dto.ClusteringResponse;
import com.example.campusactivity.client.clustering.dto.HealthResponse;

public interface ClusteringClient {
    ClusteringResponse runClustering(ClusteringRequest request);

    HealthResponse health();
}
