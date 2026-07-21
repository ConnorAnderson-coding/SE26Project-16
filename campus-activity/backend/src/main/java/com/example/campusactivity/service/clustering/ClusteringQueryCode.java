package com.example.campusactivity.service.clustering;

public enum ClusteringQueryCode {
    INVALID_RUN_ID("聚类任务标识无效"),
    INVALID_PAGE_REQUEST("分页请求无效"),
    INVALID_COMMUNITY_ID("社区标识无效"),
    INVALID_CURRENT_USER_ID("当前用户标识无效"),
    RUN_NOT_FOUND("未找到指定的聚类任务"),
    COMMUNITY_NOT_FOUND("未找到指定的社区"),
    NO_SUCCESSFUL_RUN("当前还没有可用的社区聚类结果"),
    RESULT_NOT_AVAILABLE("聚类结果尚不可用"),
    CORRUPT_STORED_DATA("聚类存储数据已损坏");

    private final String safeMessage;

    ClusteringQueryCode(String safeMessage) {
        this.safeMessage = safeMessage;
    }

    public String safeMessage() {
        return safeMessage;
    }
}
