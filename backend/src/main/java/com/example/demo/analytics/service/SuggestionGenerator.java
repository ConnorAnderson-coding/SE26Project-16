package com.example.demo.analytics.service;

import com.example.demo.analytics.dto.ActivityMetrics;
import com.example.demo.analytics.dto.SuggestionItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 改进建议生成器。
 * <p>
 * 优先调用 LLM API 生成建议，调用失败时降级为规则模板生成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionGenerator {

    private final LlmClient llmClient;
    private final SummaryBuilder summaryBuilder;

    /**
     * 为指定活动生成改进建议。
     *
     * @param metrics 活动指标数据
     * @return 建议条目列表，3-5 条
     */
    public List<SuggestionItem> generateSuggestions(ActivityMetrics metrics) {
        String summary = summaryBuilder.build(metrics);
        List<Map<String, Object>> rawList = llmClient.generateImprovements(summary);
        return rawList.stream()
                .map(this::mapToSuggestionItem)
                .toList();
    }

    /**
     * LLM 返回的 Map 转 SuggestionItem。
     */
    private SuggestionItem mapToSuggestionItem(Map<String, Object> map) {
        return SuggestionItem.builder()
                .id(String.valueOf(map.getOrDefault("id", "")))
                .category(String.valueOf(map.getOrDefault("category", "other")))
                .priority(String.valueOf(map.getOrDefault("priority", "medium")))
                .content(String.valueOf(map.getOrDefault("content", "")))
                .build();
    }

    /**
     * 公开的降级入口，供外部直接调用规则模板。
     */
    public List<SuggestionItem> fallbackSafe(ActivityMetrics metrics) {
        return fallbackGenerate(metrics);
    }

    /**
     * 降级方案：稳定生成宣传、时间、场地、内容四个维度的建议。
     */
    private List<SuggestionItem> fallbackGenerate(ActivityMetrics metrics) {
        List<SuggestionItem> suggestions = new ArrayList<>();
        int seq = 1;

        BigDecimal signupRate = metrics.getSignupRate();
        boolean lowSignup = signupRate != null && signupRate.compareTo(BigDecimal.valueOf(50)) < 0;
        suggestions.add(SuggestionItem.builder()
                .id("id-" + seq++)
                .category("promotion")
                .priority(lowSignup ? "high" : "medium")
                .content(lowSignup
                        ? "活动报名转化率仅" + signupRate + "%，建议提前一周启动学院公众号、班级群和社团渠道预热，并在文案中突出收获、嘉宾和名额稀缺性。"
                        : "活动报名转化表现尚可，建议保留当前主要宣传渠道，同时复盘带来报名的入口，把高转化文案沉淀为下次活动模板。")
                .build());

        BigDecimal attendanceRate = metrics.getAttendanceRate();
        boolean lowAttendance = attendanceRate != null && attendanceRate.compareTo(BigDecimal.valueOf(70)) < 0;
        suggestions.add(SuggestionItem.builder()
                .id("id-" + seq++)
                .category("schedule")
                .priority(lowAttendance ? "high" : "medium")
                .content(lowAttendance
                        ? "到场率仅" + attendanceRate + "%，建议在活动前一天和当天上午推送提醒，报名页增加日历提醒入口，并在现场设置清晰签到指引。"
                        : "到场率整体稳定，建议继续保留活动前提醒机制，并结合报名高峰时段选择下次推送时间，减少临近开场的爽约情况。")
                .build());

        boolean venueIssue = containsAnyFeedback(metrics,
                "场地", "教室", "投影", "屏幕", "音响", "座位", "插座", "后排", "太挤", "看不清");
        suggestions.add(SuggestionItem.builder()
                .id("id-" + seq++)
                .category("venue")
                .priority(venueIssue ? "high" : "medium")
                .content(venueIssue
                        ? "文字反馈中出现投影、座位或场地体验问题，建议下次活动前完成投影、音响、座位视线和插座检查，并准备备用教室或设备。"
                        : "场地反馈未出现明显集中问题，建议下次继续在开场前检查投影、音响、座位动线和签到区，避免设施细节影响整体评价。")
                .build());

        BigDecimal avgRating = metrics.getAvgRating();
        boolean lowRating = avgRating != null && avgRating.compareTo(BigDecimal.valueOf(4.0)) < 0;
        suggestions.add(SuggestionItem.builder()
                .id("id-" + seq)
                .category("content")
                .priority(lowRating ? "high" : "medium")
                .content(lowRating
                        ? "平均评分为" + avgRating + "/5.0，建议梳理低分评价中的共性问题，增加案例、互动练习和答疑环节，让活动内容更贴近参与者预期。"
                        : "内容评分较稳定，建议保留当前核心环节，并在结尾增加三分钟即时反馈表，收集主题深度、互动节奏和讲解清晰度的细分评价。")
                .build());

        log.info("规则模板生成 {} 条建议(activityId={})", suggestions.size(), metrics.getActivityId());
        return suggestions;
    }

    private boolean containsAnyFeedback(ActivityMetrics metrics, String... keywords) {
        if (metrics.getFeedbackContents() == null || metrics.getFeedbackContents().isEmpty()) {
            return false;
        }
        return metrics.getFeedbackContents().stream()
                .anyMatch(content -> {
                    if (content == null) {
                        return false;
                    }
                    for (String keyword : keywords) {
                        if (content.contains(keyword)) {
                            return true;
                        }
                    }
                    return false;
                });
    }
}
