package com.example.demo.common;

/**
 * Spring Cache 缓存区域名称，与 Redis key 前缀 {@code campus:} 组合使用。
 */
public final class CacheNames {

    public static final String USER_PROFILE = "user:profile";
    public static final String ACTIVITY_DETAIL = "activity:detail";
    public static final String ACTIVITY_HOT_LIST = "activity:list:hot";
    public static final String FEEDBACK_BY_ACTIVITY = "feedback:activity";
    public static final String ACTIVITY_RECORD = "activity:record";

    private CacheNames() {
    }
}
