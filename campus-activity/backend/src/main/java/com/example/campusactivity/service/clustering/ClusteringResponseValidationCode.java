package com.example.campusactivity.service.clustering;

public enum ClusteringResponseValidationCode {
    INVALID_REQUEST("聚类请求不符合校验要求"),
    INVALID_RESPONSE("聚类响应不符合校验要求"),
    RUN_ID_MISMATCH("聚类响应运行标识不匹配"),
    VERSION_MISMATCH("聚类响应版本不匹配"),
    ALGORITHM_MISMATCH("聚类响应算法不匹配"),
    CLUSTER_COUNT_MISMATCH("聚类响应社区数量参数不匹配"),
    SAMPLE_COUNT_MISMATCH("聚类响应样本数量不匹配"),
    COMMUNITY_COUNT_MISMATCH("聚类响应社区摘要数量不匹配"),
    DUPLICATE_CLUSTER_NUMBER("聚类响应包含重复社区编号"),
    INVALID_CLUSTER_NUMBER("聚类响应包含非法社区编号"),
    INCOMPLETE_CLUSTER_NUMBER_SET("聚类响应社区编号集合不完整"),
    INVALID_COMMUNITY_VALUE("聚类响应社区摘要值非法"),
    INPUT_USER_DUPLICATE("聚类请求包含重复用户"),
    RESPONSE_USER_DUPLICATE("聚类响应包含重复用户"),
    RESPONSE_USER_MISSING("聚类响应缺少输入用户"),
    RESPONSE_USER_UNEXPECTED("聚类响应包含非输入用户"),
    MEMBER_CLUSTER_UNKNOWN("聚类响应成员引用未知社区"),
    COMMUNITY_MEMBER_COUNT_MISMATCH("聚类响应社区成员数量不一致"),
    TOTAL_MEMBER_COUNT_MISMATCH("聚类响应成员总数不一致"),
    INVALID_MEMBER_ORDER("聚类响应成员顺序不符合契约"),
    INVALID_TOP_INTEREST("聚类响应代表兴趣非法"),
    DUPLICATE_TOP_INTEREST("聚类响应代表兴趣重复"),
    TOP_INTEREST_NOT_IN_CLUSTER("聚类响应包含非本社区兴趣"),
    INVALID_TOP_INTEREST_ORDER("聚类响应代表兴趣排名不符合契约"),
    INVALID_METRICS("聚类响应指标非法"),
    INVALID_MEMBER_VALUE("聚类响应成员值非法");

    private final String message;

    ClusteringResponseValidationCode(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
