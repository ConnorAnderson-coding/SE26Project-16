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
    private String status;
    private List<String> tags;
    private String checkInCode;
    private ActivityRecordResponse record;
    private Integer recommendScore;
}
