package com.example.demo.service;

import com.example.demo.dto.analytics.ActivityMetrics;
import com.example.demo.dto.analytics.SuggestionItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionGenerator {

    private final LlmClient llmClient;
    private final SummaryBuilder summaryBuilder;

    public List<SuggestionItem> generateSuggestions(ActivityMetrics metrics) {
        String summary = summaryBuilder.build(metrics);
        List<Map<String, Object>> rawList = llmClient.generateImprovements(summary);
        return rawList.stream()
                .map(this::mapToSuggestionItem)
                .toList();
    }

    private SuggestionItem mapToSuggestionItem(Map<String, Object> map) {
        return SuggestionItem.builder()
                .id(String.valueOf(map.getOrDefault("id", "")))
                .category(String.valueOf(map.getOrDefault("category", "other")))
                .priority(String.valueOf(map.getOrDefault("priority", "medium")))
                .content(String.valueOf(map.getOrDefault("content", "")))
                .build();
    }

    public List<SuggestionItem> fallbackSafe(ActivityMetrics metrics) {
        return fallbackGenerate(metrics);
    }

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
                        ? "报名转化率仅为 " + signupRate + "%，建议优化活动标题和封面图，在发布后通过学院群、社团群和首页推荐进行二次触达。"
                        : "报名转化率整体可接受，建议保留当前宣传节奏，并在活动开始前 3 天增加一次提醒，进一步稳定报名人数。")
                .build());

        BigDecimal attendanceRate = metrics.getAttendanceRate();
        boolean lowAttendance = attendanceRate != null && attendanceRate.compareTo(BigDecimal.valueOf(70)) < 0;
        suggestions.add(SuggestionItem.builder()
                .id("id-" + seq++)
                .category("schedule")
                .priority(lowAttendance ? "high" : "medium")
                .content(lowAttendance
                        ? "到场率仅为 " + attendanceRate + "%，建议避开高课业时段，并在活动前 24 小时和 2 小时分别发送提醒，降低报名后缺席。"
                        : "到场率表现稳定，建议继续沿用当前时间安排，并在签到入口设置清晰指引，减少现场排队和迟到。")
                .build());

        boolean venueIssue = containsAnyFeedback(metrics,
                "场地", "教室", "位置", "太远", "拥挤", "音响", "投影", "座位", "设备", "路线");
        suggestions.add(SuggestionItem.builder()
                .id("id-" + seq++)
                .category("venue")
                .priority(venueIssue ? "high" : "medium")
                .content(venueIssue
                        ? "反馈中出现较多场地相关问题，建议提前完成设备联调，补充路线说明，并准备备用教室或备用设备方案。"
                        : "场地反馈暂无明显异常，建议继续保留当前场地类型，并在报名页补充位置、交通和入场说明。")
                .build());

        BigDecimal avgRating = metrics.getAvgRating();
        boolean lowRating = avgRating != null && avgRating.compareTo(BigDecimal.valueOf(4.0)) < 0;
        suggestions.add(SuggestionItem.builder()
                .id("id-" + seq)
                .category("content")
                .priority(lowRating ? "high" : "medium")
                .content(lowRating
                        ? "平均评分为 " + avgRating + "/5.0，建议复盘低分反馈，压缩冗长环节，增加互动或实操内容，提高参与者获得感。"
                        : "活动内容评分较好，建议沉淀本次流程模板，并在下次加入问答、互动或成果展示环节，提升记忆点。")
                .build());

        log.info("规则模板生成 {} 条改进建议，activityId={}", suggestions.size(), metrics.getActivityId());
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
