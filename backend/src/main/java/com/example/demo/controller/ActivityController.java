package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.BusinessException;
import com.example.demo.common.PageResult;
import com.example.demo.dto.request.ActivityRequest;
import com.example.demo.dto.response.ActivityResponse;
import com.example.demo.search.ActivitySearchCriteria;
import com.example.demo.search.SearchMode;
import com.example.demo.search.service.ActivitySearchService;
import com.example.demo.service.ActivityService;
import com.example.demo.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @Autowired(required = false)
    private ActivitySearchService activitySearchService;

    @GetMapping
    public ApiResponse<PageResult<ActivityResponse>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0.7") double matchWeight) {
        return ApiResponse.ok(activityService.list(
                category, status, location, keyword, page, size, sort, matchWeight));
    }

    @GetMapping("/recommended")
    public ApiResponse<List<ActivityResponse>> recommended(
            @RequestParam(defaultValue = "6") int limit) {
        return ApiResponse.ok(activityService.getRecommended(limit));
    }

    @GetMapping("/semantic-search")
    public ApiResponse<PageResult<ActivityResponse>> semanticSearch(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "10") int size) {
        if (activitySearchService == null) {
            return ApiResponse.fail(503, "搜索服务未启用");
        }
        ActivitySearchCriteria criteria = new ActivitySearchCriteria(
                query, null, null, null,
                SearchMode.SEMANTIC, null, 0.5, 0, size);
        return ApiResponse.ok(activitySearchService.search(criteria));
    }

    @PostMapping("/semantic-search/reindex")
    public ApiResponse<Boolean> rebuildSemanticIndex() {
        if (activitySearchService == null) {
            return ApiResponse.fail(503, "搜索服务未启用");
        }
        if (!"admin".equals(SecurityUtils.getCurrentUser().getUser().getRole())) {
            throw new BusinessException(403, "Only admins can rebuild the semantic search index");
        }
        return ApiResponse.ok(activitySearchService.isIndexEmpty());
    }

    @GetMapping("/mine")
    public ApiResponse<List<ActivityResponse>> mine() {
        return ApiResponse.ok(activityService.getMine());
    }

    @GetMapping("/{id}")
    public ApiResponse<ActivityResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(activityService.getById(id));
    }

    @PostMapping
    public ApiResponse<ActivityResponse> create(@Valid @RequestBody ActivityRequest request) {
        return ApiResponse.ok(activityService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ActivityResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ActivityRequest request) {
        return ApiResponse.ok(activityService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        activityService.delete(id);
        return ApiResponse.ok();
    }
}
