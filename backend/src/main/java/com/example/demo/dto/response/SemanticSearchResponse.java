package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SemanticSearchResponse {
    private String query;
    private boolean elasticsearchEnabled;
    private boolean fallback;
    private List<ActivityResponse> results;
}
