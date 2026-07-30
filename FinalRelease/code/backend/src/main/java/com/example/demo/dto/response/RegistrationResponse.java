package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RegistrationResponse {
    private Long id;
    private Long activityId;
    private String activityTitle;
    private String userId;
    private String userName;
    private String college;
    private String status;
    private LocalDateTime createdAt;
    private String location;
    private LocalDateTime startTime;
}
