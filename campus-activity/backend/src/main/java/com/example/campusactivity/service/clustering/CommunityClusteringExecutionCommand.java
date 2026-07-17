package com.example.campusactivity.service.clustering;

public record CommunityClusteringExecutionCommand(
        int clusterCount,
        String createdBy
) {
    private static final int MAX_CREATED_BY_LENGTH = 255;

    public CommunityClusteringExecutionCommand {
        if (clusterCount < 2) {
            throw new IllegalArgumentException("clusterCount 必须至少为2");
        }
        if (createdBy == null
                || createdBy.isBlank()
                || createdBy.length() > MAX_CREATED_BY_LENGTH) {
            throw new IllegalArgumentException("createdBy 不符合聚类运行字段约束");
        }
    }
}
