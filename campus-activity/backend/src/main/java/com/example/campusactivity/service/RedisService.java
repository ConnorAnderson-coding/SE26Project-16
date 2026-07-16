package com.example.campusactivity.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    private static final String HOT_RANK_KEY = "hot:rank";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * Add or update an activity's hotness score in the Redis ZSET.
     * @param activityId The ID of the activity.
     * @param hotnessScore The hotness score of the activity.
     */
    public void addOrUpdateActivityHotness(String activityId, double hotnessScore) {
        stringRedisTemplate.opsForZSet().add(HOT_RANK_KEY, activityId, hotnessScore);
    }

    /**
     * Get top N activity IDs by hotness score.
     * @param limit The maximum number of activity IDs to return.
     * @return A Set of activity IDs.
     */
    public Set<String> getTopNActivityIds(long limit) {
        return stringRedisTemplate.opsForZSet().reverseRange(HOT_RANK_KEY, 0, limit - 1);
    }

    /**
     * Get a range of activity IDs by hotness score (for pagination).
     * @param start The starting index.
     * @param end The ending index.
     * @return A Set of activity IDs.
     */
    public Set<String> getActivitiesByHotnessRange(long start, long end) {
        return stringRedisTemplate.opsForZSet().reverseRange(HOT_RANK_KEY, start, end);
    }

    /**
     * Remove an activity from the hotness rank.
     * @param activityId The ID of the activity to remove.
     */
    public void removeActivityFromRank(String activityId) {
        stringRedisTemplate.opsForZSet().remove(HOT_RANK_KEY, activityId);
    }

    /**
     * Get the hotness score of a specific activity.
     * @param activityId The ID of the activity.
     * @return The hotness score, or null if not found.
     */
    public Double getActivityHotness(String activityId) {
        return stringRedisTemplate.opsForZSet().score(HOT_RANK_KEY, activityId);
    }

    /**
     * Increment the view count for an activity. This is a temporary method for now.
     * In a real application, this might be handled differently (e.g., dedicated view service).
     * @param activityId The ID of the activity.
     */
    public void incrementActivityViewCount(String activityId) {
        // This could be used for real-time view counting if needed, then aggregated for hotness.
        // For now, hotness calculation directly uses the viewCount from the Activity entity.
        // This method is just an example of Redis usage.
        stringRedisTemplate.opsForValue().increment("activity:views:" + activityId, 1);
    }

    /**
     * Get the view count for an activity.
     * @param activityId The ID of the activity.
     * @return The view count.
     */
    public Long getActivityViewCount(String activityId) {
        String views = stringRedisTemplate.opsForValue().get("activity:views:" + activityId);
        return views != null ? Long.parseLong(views) : 0L;
    }

}
