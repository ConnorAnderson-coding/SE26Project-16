package com.example.campusactivity.client.clustering.exception;

public abstract class ClusteringClientException extends RuntimeException {
    public static final String UNKNOWN_REMOTE_ERROR = "UNKNOWN_REMOTE_ERROR";

    protected ClusteringClientException(String message) {
        super(message);
    }
}
