package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FeedbackResponse {
    private Long id;
    private Long activityId;
    private String activityTitle;
    private String userId;
    private String userName;
    private Integer rating;
    private String content;
    private LocalDateTime createdAt;
}
