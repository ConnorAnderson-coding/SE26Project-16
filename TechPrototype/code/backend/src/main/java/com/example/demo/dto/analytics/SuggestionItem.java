package com.example.demo.dto.analytics;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SuggestionItem {

    private String id;

    private String category;

    private String priority;

    private String content;
}
