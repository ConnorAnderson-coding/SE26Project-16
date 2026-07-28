package com.example.demo.community.service;

import com.example.demo.community.client.ClusteringContracts.Request;

public interface ClusteringRequestFactory {

    Request buildRequest(String runId);
}
