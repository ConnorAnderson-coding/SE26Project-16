package com.example.demo.analytics.service;

import com.example.demo.analytics.dto.ActivityMetrics;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 数据摘要构建器
 * <p>
 * 将活动指标数据格式化为结构化文本摘要，作为 LLM 的输入上下文。
 * 用户文字评价涉及隐私，传入前做脱敏处理（不包含用户ID/姓名）。
 */
@Service
public class SummaryBuilder {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "sports", "体育活动",
            "academic", "学术讲座",
            "culture", "文化艺术",
            "volunteer", "志愿服务",
            "competition", "竞赛比赛",
            "entertainment", "娱乐休闲",
            "other", "其他"
    );

    /**
     * 构建活动数据摘要文本
     *
     * @param metrics 活动指标数据
     * @return 结构化文本摘要
     */
    public String build(ActivityMetrics metrics) {
        var sb = new StringJoiner("\n");

        // ── 活动基本信息 ──
        sb.add("## 活动信息");
        sb.add("- 名称：" + metrics.getActivityTitle());
        sb.add("- 类别：" + toCategoryLabel(metrics.getCategory()));
        sb.add("- 地点：" + metrics.getLocation());
        if (metrics.getStartTime() != null || metrics.getEndTime() != null) {
            sb.add("- 活动时间：" + formatTime(metrics.getStartTime()) + " 至 " + formatTime(metrics.getEndTime()));
        }

        // ── 核心指标 ──
        sb.add("## 核心指标");
        sb.add("- 浏览量：" + metrics.getViewCount() + " 次");
        sb.add("- 报名转化率：" + fmt(metrics.getSignupRate()) + "%"
                + " — 报名 " + metrics.getSignupCount() + "/" + metrics.getMaxParticipants() + " 人");
        sb.add("- 审核通过：" + metrics.getApprovedCount() + " 人");
        sb.add("- 到场率：" + fmt(metrics.getAttendanceRate()) + "%"
                + " — 签到 " + metrics.getCheckInCount() + "/" + metrics.getSignupCount() + " 人");

        // ── 评分指标 ──
        sb.add("## 评分指标");
        if (metrics.getFeedbackCount() > 0) {
            sb.add("- 评价总数：" + metrics.getFeedbackCount() + " 条");
            sb.add("- 平均评分：" + fmt2(metrics.getAvgRating()) + " / 5.00");
            sb.add("- 评分分布：" + formatRatingDistribution(metrics.getRatingDistribution()));
            // ── 用户评价内容（已脱敏，不包含用户身份信息） ──
            if (metrics.getFeedbackContents() != null && !metrics.getFeedbackContents().isEmpty()) {
                sb.add("## 用户评价摘要（已脱敏）");
                var feedbacks = metrics.getFeedbackContents();
                int showCount = Math.min(feedbacks.size(), 8);
                for (int i = 0; i < showCount; i++) {
                    sb.add("- 评价" + (i + 1) + "：" + feedbacks.get(i));
                }
            }
        } else {
            sb.add("- 暂无评价");
        }

        // ── 签到方式 ──
        if (metrics.getCheckInMethodsStats() != null && !metrics.getCheckInMethodsStats().isEmpty()) {
            sb.add("## 签到方式分布");
            metrics.getCheckInMethodsStats().forEach((method, count) ->
                    sb.add("- " + toMethodLabel(method) + "：" + count + " 人"));
        }

        return sb.toString();
    }

    private String toCategoryLabel(String category) {
        return CATEGORY_LABELS.getOrDefault(category, category);
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "未设置" : time.format(TIME_FORMATTER);
    }

    private String toMethodLabel(String method) {
        return switch (method) {
            case "qrcode" -> "二维码签到";
            case "location" -> "定位签到";
            case "password" -> "动态口令";
            default -> method;
        };
    }

    private String formatRatingDistribution(Map<Integer, Long> dist) {
        if (dist == null) return "无数据";
        var sb = new StringBuilder();
        for (int i = 5; i >= 1; i--) {
            sb.append(i).append("★:").append(dist.getOrDefault(i, 0L));
            if (i > 1) sb.append(" | ");
        }
        return sb.toString();
    }

    private static String fmt(BigDecimal v) {
        return v != null ? v.toPlainString() : "0";
    }

    private static String fmt2(BigDecimal v) {
        return v != null ? v.toPlainString() : "N/A";
    }
}
