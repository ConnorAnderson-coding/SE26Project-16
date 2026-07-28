package com.example.demo.community.api;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ClusteringApiException extends RuntimeException {

    private final HttpStatus status;

    public ClusteringApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
