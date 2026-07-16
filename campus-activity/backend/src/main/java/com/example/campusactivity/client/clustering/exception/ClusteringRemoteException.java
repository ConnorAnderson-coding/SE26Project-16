package com.example.campusactivity.client.clustering.exception;

import java.util.Map;

public final class ClusteringRemoteException extends ClusteringClientException {
    private final int statusCode;
    private final String errorCode;
    private final String remoteMessage;
    private final Map<String, Object> details;

    public ClusteringRemoteException(
            int statusCode,
            String errorCode,
            String remoteMessage,
            Map<String, Object> _details
    ) {
        super("Python 聚类服务返回业务错误");
        this.statusCode = statusCode;
        this.errorCode = RemoteErrorCodePolicy.sanitize(errorCode);
        this.remoteMessage = remoteMessage;
        this.details = Map.of();
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getRemoteMessage() {
        return remoteMessage;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
