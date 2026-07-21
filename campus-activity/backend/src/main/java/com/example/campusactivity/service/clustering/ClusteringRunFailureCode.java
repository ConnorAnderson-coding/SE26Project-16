package com.example.campusactivity.service.clustering;

public enum ClusteringRunFailureCode {
    FEATURE_AGGREGATION_FAILED("特征聚合失败"),
    NO_EFFECTIVE_USERS("有效聚类用户不足"),
    INVALID_CLUSTER_COUNT("聚类数量无效"),
    ACTIVE_RUN_EXISTS("已有聚类运行正在处理"),
    RUN_STATE_CONFLICT("聚类运行状态冲突"),
    INPUT_SNAPSHOT_INVALID("聚类输入快照缺失或损坏"),
    DISPATCH_REJECTED("后台执行器拒绝聚类任务"),
    EXECUTION_INTERRUPTED("应用重启前的聚类执行已中断"),
    PYTHON_REQUEST_REJECTED("聚类服务拒绝请求"),
    PYTHON_SERVICE_UNAVAILABLE("聚类服务不可用"),
    PYTHON_PROTOCOL_ERROR("聚类服务响应协议错误"),
    INVALID_CLUSTERING_RESULT("聚类结果校验失败"),
    USER_REFERENCE_MISSING("聚类结果引用的用户不存在"),
    RESULT_SERIALIZATION_FAILED("聚类持久化数据序列化失败"),
    CLUSTERING_SERVICE_FAILED("聚类计算失败"),
    RESULT_VALIDATION_FAILED("聚类结果校验失败"),
    RESULT_PERSISTENCE_FAILED("聚类结果持久化失败"),
    INTERNAL_ERROR("聚类运行发生内部错误"),
    UNEXPECTED_INTERNAL_FAILURE("聚类运行发生内部失败");

    private final String safeSummary;

    ClusteringRunFailureCode(String safeSummary) {
        this.safeSummary = safeSummary;
    }

    public String errorMessage() {
        return name() + ": " + safeSummary;
    }
}
