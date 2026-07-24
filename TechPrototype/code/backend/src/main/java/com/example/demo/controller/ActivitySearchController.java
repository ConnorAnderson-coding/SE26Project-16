package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.PageResult;
import com.example.demo.dto.response.ActivityResponse;
import com.example.demo.search.ActivitySearchCriteria;
import com.example.demo.search.SearchMode;
import com.example.demo.search.SearchSort;
import com.example.demo.search.service.ActivitySearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class ActivitySearchController {

    private final ActivitySearchService activitySearchService;

    @GetMapping("/activities")
    public ApiResponse<PageResult<ActivityResponse>> searchActivities(
            @RequestParam String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "hybrid") String mode,
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "0.7") double matchWeight,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ActivitySearchCriteria criteria = new ActivitySearchCriteria(
                keyword,
                category,
                status,
                location,
                SearchMode.from(mode),
                SearchSort.from(sort),
                matchWeight,
                page,
                size);
        return ApiResponse.ok(activitySearchService.search(criteria));
    }
}
