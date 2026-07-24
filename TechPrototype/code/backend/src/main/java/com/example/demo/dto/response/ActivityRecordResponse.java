package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ActivityRecordResponse {
    private String summary;
    private List<String> photos;
    private LocalDateTime publishedAt;
}
