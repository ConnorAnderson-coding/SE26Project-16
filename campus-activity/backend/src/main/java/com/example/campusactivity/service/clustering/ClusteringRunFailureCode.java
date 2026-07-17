package com.example.campusactivity.service.clustering;

public enum ClusteringRunFailureCode {
    FEATURE_AGGREGATION_FAILED("特征聚合失败"),
    CLUSTERING_SERVICE_FAILED("聚类计算失败"),
    RESULT_VALIDATION_FAILED("聚类结果校验失败"),
    RESULT_PERSISTENCE_FAILED("聚类结果持久化失败"),
    UNEXPECTED_INTERNAL_FAILURE("聚类运行发生内部失败");

    private final String safeSummary;

    ClusteringRunFailureCode(String safeSummary) {
        this.safeSummary = safeSummary;
    }

    public String errorMessage() {
        return name() + ": " + safeSummary;
    }
}
