package com.example.demo.community.client;

import lombok.Getter;

@Getter
public class ClusteringClientException extends RuntimeException {

    public enum Kind {
        UNAVAILABLE,
        REMOTE_REJECTION,
        INVALID_RESPONSE
    }

    private final Kind kind;
    private final Integer statusCode;
    private final String remoteCode;

    public ClusteringClientException(Kind kind, String message) {
        this(kind, null, null, message, null);
    }

    public ClusteringClientException(
            Kind kind,
            Integer statusCode,
            String remoteCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.kind = kind;
        this.statusCode = statusCode;
        this.remoteCode = remoteCode;
    }
}
