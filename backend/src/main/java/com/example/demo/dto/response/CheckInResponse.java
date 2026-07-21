package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CheckInResponse {
    private Long id;
    private Long activityId;
    private String activityTitle;
    private String userId;
    private String userName;
    private String method;
    private LocalDateTime time;
    private Double latitude;
    private Double longitude;
    private Double distanceMeters;
}
