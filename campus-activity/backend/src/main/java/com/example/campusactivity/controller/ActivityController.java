package com.example.campusactivity.controller;

import com.example.campusactivity.dto.RecordRequest;
import com.example.campusactivity.entity.Activity;
import com.example.campusactivity.repository.ActivityRepository;
import com.example.campusactivity.service.RedisService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {
    private final ActivityRepository activityRepository;
    private final RedisService redisService;

    public ActivityController(ActivityRepository activityRepository, RedisService redisService) {
        this.activityRepository = activityRepository;
        this.redisService = redisService;
    }

    @GetMapping
    public List<Activity> list(@RequestParam(required = false) String category,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) String q,
                               @RequestParam(required = false) String location,
                               @RequestParam(required = false) String tag,
                               @RequestParam(required = false) String sort) {
        return activityRepository.findAll().stream()
                .filter(a -> category == null || category.equals(a.getCategory()))
                .filter(a -> status == null || status.equals(a.getStatus()))
                .filter(a -> location == null || safe(a.getLocation()).contains(location))
                .filter(a -> q == null || safe(a.getTitle()).contains(q) || safe(a.getDescription()).contains(q))
                .filter(a -> tag == null || a.getTags().contains(tag))
                .sorted(comparator(sort))
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Activity> get(@PathVariable String id) {
        return activityRepository.findById(id).map(activity -> {
            activity.setViewCount(activity.getViewCount() + 1);
            return ResponseEntity.ok(activityRepository.save(activity));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Activity create(@Valid @RequestBody Activity activity) {
        return activityRepository.save(activity);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Activity> update(@PathVariable String id, @Valid @RequestBody Activity activity) {
        if (!activityRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        activity.setId(id);
        return ResponseEntity.ok(activityRepository.save(activity));
    }

    @PutMapping("/{id}/record")
    public ResponseEntity<Activity> publishRecord(@PathVariable String id, @RequestBody RecordRequest request) {
        return activityRepository.findById(id)
                .map(activity -> {
                    activity.setStatus("ended");
                    activity.setRecordSummary(request.summary());
                    activity.setRecordPhotos(request.photos());
                    activity.setRecordPublishedAt(LocalDateTime.now());
                    return ResponseEntity.ok(activityRepository.save(activity));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!activityRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        activityRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static Comparator<Activity> comparator(String sort) {
        if ("hot".equals(sort)) {
            return Comparator.comparing(Activity::getHotness, Comparator.nullsLast(Double::compareTo)).reversed();
        }
        return Comparator.comparing(Activity::getStartTime, Comparator.nullsLast(LocalDateTime::compareTo));
    }

    @GetMapping("/hot")
    public ResponseEntity<List<Activity>> getHotActivities(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        long start = (long) page * size;
        long end = start + size - 1;
        Set<String> hotActivityIds = redisService.getActivitiesByHotnessRange(start, end);

        List<Activity> hotActivities = hotActivityIds.stream()
                .map(activityRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .sorted(Comparator.comparing(Activity::getHotness, Comparator.nullsLast(Double::compareTo)).reversed())
                .collect(Collectors.toList());

        return ResponseEntity.ok(hotActivities);
    }
}
