package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CheckInSessionResponse {
    private Long activityId;
    private String token;
    private String qrContent;
    private String code;
    private Integer expiresInSeconds;
    private LocalDateTime issuedAt;
}
