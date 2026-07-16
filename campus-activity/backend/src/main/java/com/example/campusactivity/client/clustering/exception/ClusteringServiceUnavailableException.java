package com.example.campusactivity.client.clustering.exception;

public final class ClusteringServiceUnavailableException extends ClusteringClientException {
    private final Integer statusCode;
    private final String serviceErrorCode;

    public ClusteringServiceUnavailableException() {
        super("Python 聚类服务当前不可用");
        this.statusCode = null;
        this.serviceErrorCode = null;
    }

    public ClusteringServiceUnavailableException(int statusCode, String serviceErrorCode) {
        super("Python 聚类服务当前不可用");
        this.statusCode = statusCode;
        this.serviceErrorCode = RemoteErrorCodePolicy.sanitize(serviceErrorCode);
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getServiceErrorCode() {
        return serviceErrorCode;
    }
}
