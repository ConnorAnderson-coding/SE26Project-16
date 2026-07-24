package com.example.demo.service;

import com.example.demo.dto.analytics.ActivityMetrics;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.StringJoiner;

@Service
public class SummaryBuilder {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "sports", "体育运动",
            "academic", "学术讲座",
            "culture", "文化艺术",
            "volunteer", "志愿公益",
            "competition", "竞赛比赛",
            "entertainment", "休闲娱乐",
            "other", "其他"
    );

    public String build(ActivityMetrics metrics) {
        var sb = new StringJoiner("\n");

        sb.add("## 活动基本信息");
        sb.add("- 标题：" + metrics.getActivityTitle());
        sb.add("- 类别：" + toCategoryLabel(metrics.getCategory()));
        sb.add("- 地点：" + metrics.getLocation());
        if (metrics.getStartTime() != null || metrics.getEndTime() != null) {
            sb.add("- 活动时间：" + formatTime(metrics.getStartTime()) + " 至 " + formatTime(metrics.getEndTime()));
        }

        sb.add("## 核心指标");
        sb.add("- 浏览量：" + metrics.getViewCount() + " 次");
        sb.add("- 报名转化率：" + fmt(metrics.getSignupRate()) + "%"
                + "（报名人数：" + metrics.getSignupCount() + "/" + metrics.getViewCount() + " 次浏览）");
        appendCapacityUtilization(sb, metrics);
        sb.add("- 已通过人数：" + metrics.getApprovedCount() + " 人");
        sb.add("- 到场率：" + fmt(metrics.getAttendanceRate()) + "%"
                + "（签到人数：" + metrics.getCheckInCount() + "/" + metrics.getSignupCount() + "）");

        sb.add("## 评价数据");
        if (metrics.getFeedbackCount() > 0) {
            sb.add("- 评价数量：" + metrics.getFeedbackCount() + " 条");
            sb.add("- 平均评分：" + fmt2(metrics.getAvgRating()) + " / 5.00");
            sb.add("- 评分分布：" + formatRatingDistribution(metrics.getRatingDistribution()));

            if (metrics.getFeedbackContents() != null && !metrics.getFeedbackContents().isEmpty()) {
                sb.add("## 文字反馈摘要（已脱敏）");
                var feedbacks = metrics.getFeedbackContents();
                int showCount = Math.min(feedbacks.size(), 8);
                for (int i = 0; i < showCount; i++) {
                    sb.add("- 反馈" + (i + 1) + "：" + feedbacks.get(i));
                }
            }
        } else {
            sb.add("- 暂无评价");
        }

        if (metrics.getCheckInMethodsStats() != null && !metrics.getCheckInMethodsStats().isEmpty()) {
            sb.add("## 签到方式统计");
            metrics.getCheckInMethodsStats().forEach((method, count) ->
                    sb.add("- " + toMethodLabel(method) + "：" + count + " 人"));
        }

        return sb.toString();
    }

    
    private void appendCapacityUtilization(StringJoiner sb, ActivityMetrics metrics) {
        Integer signupCount = metrics.getSignupCount();
        Integer maxParticipants = metrics.getMaxParticipants();
        if (signupCount == null || maxParticipants == null || maxParticipants <= 0) {
            return;
        }
        BigDecimal rate = BigDecimal.valueOf(signupCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(maxParticipants), 1, RoundingMode.HALF_UP);
        sb.add("- 容量使用率：" + rate.toPlainString() + "%"
                + "（报名人数：" + signupCount + "/" + maxParticipants + " 容量）");
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
            case "password" -> "签到码签到";
            default -> method;
        };
    }

    private String formatRatingDistribution(Map<Integer, Long> dist) {
        if (dist == null) {
            return "暂无数据";
        }
        var sb = new StringBuilder();
        for (int i = 5; i >= 1; i--) {
            sb.append(i).append("星：").append(dist.getOrDefault(i, 0L));
            if (i > 1) {
                sb.append(" | ");
            }
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
