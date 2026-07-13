package com.example.demo.analytics.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 改进建议条目
 */
@Data
@Builder
public class SuggestionItem {

    /** 建议ID */
    private String id;

    /**
     * 建议类别:
     * <ul>
     *   <li>promotion — 宣传推广</li>
     *   <li>schedule  — 时间安排</li>
     *   <li>venue     — 场地设施</li>
     *   <li>content   — 内容质量</li>
     *   <li>other     — 其他</li>
     * </ul>
     */
    private String category;

    /** 优先级: high / medium / low */
    private String priority;

    /** 建议内容（中文，40-100字） */
    private String content;
}
