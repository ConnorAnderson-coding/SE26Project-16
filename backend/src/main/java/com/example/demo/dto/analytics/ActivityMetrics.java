package com.example.demo.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityMetrics {

    private Long activityId;

    private String activityTitle;

    private String category;

    private String location;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer viewCount;

    private Integer signupCount;

    private Integer maxParticipants;

    private BigDecimal signupRate;

    private Long approvedCount;

    private Long checkInCount;

    private BigDecimal attendanceRate;

    private Integer favoriteCount;

    private Long feedbackCount;

    private BigDecimal avgRating;

    private Map<Integer, Long> ratingDistribution;

    private Map<String, Long> checkInMethodsStats;

    private List<String> feedbackContents;

    private Map<String, Long> signupTrend;
}
