package com.example.demo.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActivityDocument(
        Long id,
        String title,
        String description,
        String category,
        String location,
        String college,
        @JsonProperty("organizer_id") String organizerId,
        String status,
        List<String> tags,
        @JsonProperty("start_time") LocalDateTime startTime,
        @JsonProperty("end_time") LocalDateTime endTime,
        @JsonProperty("signup_count") Integer signupCount,
        @JsonProperty("favorite_count") Integer favoriteCount,
        @JsonProperty("search_text") String searchText) {
}
