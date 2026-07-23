package com.example.demo.community.service;

import lombok.Getter;

@Getter
public class ClusteringStateException extends RuntimeException {

    public enum Code {
        ACTIVE_RUN_EXISTS,
        INVALID_PARAMETERS,
        RUN_NOT_FOUND,
        INVALID_STATE_TRANSITION,
        INVALID_REMOTE_RESULT,
        RESULT_PERSISTENCE_FAILED
    }

    private final Code code;

    public ClusteringStateException(Code code) {
        super(code.name());
        this.code = code;
    }

    public ClusteringStateException(Code code, Throwable cause) {
        super(code.name(), cause);
        this.code = code;
    }
}
