package com.example.campusactivity.controller;

import com.example.campusactivity.dto.ApiResponse;
import com.example.campusactivity.entity.Activity;
import com.example.campusactivity.entity.Favorite;
import com.example.campusactivity.repository.ActivityRepository;
import com.example.campusactivity.repository.FavoriteRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FavoriteController {
    private final FavoriteRepository favoriteRepository;
    private final ActivityRepository activityRepository;

    public FavoriteController(FavoriteRepository favoriteRepository, ActivityRepository activityRepository) {
        this.favoriteRepository = favoriteRepository;
        this.activityRepository = activityRepository;
    }

    @GetMapping("/favorites")
    public List<Favorite> list(@RequestParam(required = false) String userId) {
        return userId == null ? favoriteRepository.findAll() : favoriteRepository.findByUserId(userId);
    }

    @PostMapping("/favorites")
    public Favorite create(@RequestBody Favorite favorite) {
        return favoriteRepository.save(favorite);
    }

    @PostMapping("/activities/{activityId}/favorite")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> toggle(@PathVariable String activityId,
                                                                    @RequestParam String userId) {
        Activity activity = activityRepository.findById(activityId).orElse(null);
        if (activity == null) {
            return ResponseEntity.notFound().build();
        }

        boolean favorited;
        var existing = favoriteRepository.findByUserIdAndActivityId(userId, activityId);
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            activity.setFavoriteCount(Math.max(0, (activity.getFavoriteCount() == null ? 0 : activity.getFavoriteCount()) - 1));
            favorited = false;
        } else {
            Favorite favorite = new Favorite();
            favorite.setUserId(userId);
            favorite.setActivityId(activityId);
            favoriteRepository.save(favorite);
            activity.setFavoriteCount((activity.getFavoriteCount() == null ? 0 : activity.getFavoriteCount()) + 1);
            favorited = true;
        }
        activityRepository.save(activity);
        return ResponseEntity.ok(ApiResponse.ok(favorited ? "已收藏" : "已取消收藏", Map.of("favorited", favorited)));
    }

    @DeleteMapping("/favorites/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!favoriteRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        favoriteRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
