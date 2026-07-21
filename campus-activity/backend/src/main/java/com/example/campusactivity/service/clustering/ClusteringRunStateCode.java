package com.example.campusactivity.service.clustering;

public enum ClusteringRunStateCode {
    ACTIVE_RUN_EXISTS("当前已有聚类运行正在处理"),
    RUN_CREATION_CONFLICT("聚类运行创建发生冲突"),
    RUN_NOT_FOUND("未找到指定的聚类运行"),
    INVALID_INITIAL_PARAMETERS("聚类运行初始参数无效"),
    INVALID_STATE_TRANSITION("聚类运行状态转换无效"),
    RUN_ALREADY_TERMINAL("聚类运行已经结束"),
    RUN_RESULTS_ALREADY_EXIST("聚类运行结果已经存在"),
    RUN_RESULT_MISMATCH("聚类结果与运行记录不匹配"),
    USER_REFERENCE_MISSING("聚类结果引用的用户不存在"),
    RESULT_SERIALIZATION_FAILED("聚类持久化数据序列化失败"),
    RESULT_PERSISTENCE_FAILED("聚类结果持久化失败");

    private final String safeMessage;

    ClusteringRunStateCode(String safeMessage) {
        this.safeMessage = safeMessage;
    }

    public String safeMessage() {
        return safeMessage;
    }
}
