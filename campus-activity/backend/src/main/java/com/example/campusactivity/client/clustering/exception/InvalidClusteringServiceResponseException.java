package com.example.campusactivity.client.clustering.exception;

public final class InvalidClusteringServiceResponseException extends ClusteringClientException {
    public InvalidClusteringServiceResponseException() {
        super("Python 聚类服务返回了不符合契约的响应");
    }
}
