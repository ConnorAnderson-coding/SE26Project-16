package com.example.campusactivity.controller;

final class InvalidClusteringRequestException extends RuntimeException {
    InvalidClusteringRequestException() {
        super("INVALID_CLUSTERING_REQUEST");
    }
}
