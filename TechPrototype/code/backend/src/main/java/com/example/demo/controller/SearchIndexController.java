package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.search.ActivityIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search/index")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class SearchIndexController {

    private final ActivityIndexService activityIndexService;

    @PostMapping("/rebuild")
    public ApiResponse<ActivityIndexService.IndexRebuildResult> rebuild() {
        return ApiResponse.ok(activityIndexService.rebuildAll());
    }

    @GetMapping("/stats")
    public ApiResponse<ActivityIndexService.IndexStats> stats() {
        return ApiResponse.ok(activityIndexService.getStats());
    }
}
