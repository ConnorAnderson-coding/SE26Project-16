package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CheckInStatsResponse {
    private Long activityId;
    private Long registeredCount;
    private Long checkedInCount;
    private Long uncheckedCount;
    private Double checkInRate;
    private List<CheckInResponse> records;
}
