package com.example.demo.search;

import com.example.demo.entity.Activity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ActivityDocumentMapper {

    /** Chinese labels aligned with frontend ACTIVITY_CATEGORIES (organizer-facing). */
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "academic", "学术讲座",
            "sports", "体育运动",
            "club", "社团活动",
            "arts", "文艺表演",
            "volunteer", "志愿服务",
            "innovation", "创新创业");

    private ActivityDocumentMapper() {
    }

    public static ActivityDocument toDocument(Activity activity) {
        String organizerId = activity.getOrganizerId();
        if (organizerId == null && activity.getOrganizer() != null) {
            organizerId = activity.getOrganizer().getId();
        }

        return new ActivityDocument(
                activity.getId(),
                activity.getTitle(),
                activity.getDescription(),
                activity.getCategory(),
                activity.getLocation(),
                activity.getCollege(),
                organizerId,
                activity.getStatus(),
                activity.getTags(),
                activity.getStartTime(),
                activity.getEndTime(),
                activity.getSignupCount(),
                activity.getFavoriteCount(),
                buildSearchText(activity));
    }

    /**
     * Activity content for BM25 and GTE embedding (title/description/category/tags/location).
     * Excludes college: many activities share an organizer college (e.g. 计算机学院) and that
     * causes false BM25 hits for queries like「计算机科学」. College remains a filterable field.
     * Also excludes records, feedback, and engagement counts.
     */
    static String buildSearchText(Activity activity) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, activity.getTitle());
        addIfPresent(parts, activity.getDescription());
        addCategoryParts(parts, activity.getCategory());
        addTags(parts, activity.getTags());
        addIfPresent(parts, activity.getLocation());
        return String.join(" ", parts).trim();
    }

    private static void addCategoryParts(List<String> parts, String category) {
        if (category == null || category.isBlank()) {
            return;
        }
        String code = category.trim();
        addIfPresent(parts, code);
        String label = CATEGORY_LABELS.get(code.toLowerCase(Locale.ROOT));
        if (label != null) {
            addIfPresent(parts, label);
        }
    }

    private static void addTags(List<String> parts, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                seen.add(tag.trim());
            }
        }
        parts.addAll(seen);
    }

    private static void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }

    public static boolean isIndexable(Activity activity) {
        return activity.getStatus() != null && !"draft".equals(activity.getStatus());
    }
}
