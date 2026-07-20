package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ActivityResponse {
    private Long id;
    private String title;
    private String category;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String location;
    private String organizerId;
    private String organizerName;
    private String college;
    private String poster;
    private Integer maxParticipants;
    private Integer signupCount;
    private Integer favoriteCount;
    /** 综合热度分（浏览/报名/签到/收藏加权 + 时间衰减 + 状态修正） */
    private Double hotnessScore;
    private String status;
    private List<String> tags;
    private String checkInCode;
    private Double latitude;
    private Double longitude;
    private Integer checkInRadiusMeters;
    private ActivityRecordResponse record;
    private Integer recommendScore;
    /** Short Chinese labels explaining why this activity was recommended. */
    private List<String> recommendReasons;
    private Double searchScore;
    private String searchChannel;
    private Double keywordScore;
    private Double semanticScore;
    private Double compositeScore;
}
