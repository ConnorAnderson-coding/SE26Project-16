package com.example.demo.community.client;

import com.example.demo.community.client.ClusteringContracts.HealthResponse;
import com.example.demo.community.client.ClusteringContracts.Request;
import com.example.demo.community.client.ClusteringContracts.Response;

public interface ClusteringClient {

    Response runClustering(Request request);

    HealthResponse health();
}
