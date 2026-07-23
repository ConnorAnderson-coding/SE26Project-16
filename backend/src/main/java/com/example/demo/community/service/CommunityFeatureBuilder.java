package com.example.demo.community.service;

import com.example.demo.community.client.ClusteringContracts.FeatureSample;
import com.example.demo.entity.User;
import com.example.demo.repository.CheckInRepository;
import com.example.demo.repository.FavoriteRepository;
import com.example.demo.repository.FeedbackRepository;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.projection.UserBehaviorCount;
import com.example.demo.repository.projection.UserCategoryCount;
import com.example.demo.repository.projection.UserFeedbackAggregate;
import com.example.demo.repository.projection.UserRegistrationAggregate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CommunityFeatureBuilder {

    public static final String FEATURE_SCHEMA_VERSION = "community-features-v2";
    public static final int WINDOW_DAYS = 180;
    private static final List<String> NUMERIC_FEATURES = List.of(
            "logSignupCount",
            "approvedRate",
            "logFavoriteCount",
            "logCheckInCount",
            "attendanceRate",
            "logFeedbackCount",
            "averageRating",
            "hasAverageRating"
    );
    private static final Set<String> ELIGIBLE_ROLES = Set.of("student", "teacher");

    private final UserRepository userRepository;
    private final RegistrationRepository registrationRepository;
    private final CheckInRepository checkInRepository;
    private final FavoriteRepository favoriteRepository;
    private final FeedbackRepository feedbackRepository;
    private final Clock clock;

    public CommunityFeatureBuilder(
            UserRepository userRepository,
            RegistrationRepository registrationRepository,
            CheckInRepository checkInRepository,
            FavoriteRepository favoriteRepository,
            FeedbackRepository feedbackRepository,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.registrationRepository = registrationRepository;
        this.checkInRepository = checkInRepository;
        this.favoriteRepository = favoriteRepository;
        this.feedbackRepository = feedbackRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CommunityFeatureSnapshot build() {
        LocalDateTime windowStart = LocalDateTime.now(clock).minusDays(WINDOW_DAYS);
        List<User> users = userRepository.findByRoleInOrderByIdAsc(ELIGIBLE_ROLES);
        Set<String> eligibleIds = users.stream().map(User::getId).collect(Collectors.toSet());

        Map<String, UserRegistrationAggregate> registrations = registrationRepository
                .aggregateByUserSince(windowStart).stream()
                .filter(row -> eligibleIds.contains(row.getUserId()))
                .collect(Collectors.toMap(UserRegistrationAggregate::getUserId, Function.identity()));
        Map<String, Integer> checkIns = countMap(checkInRepository.countByUserSince(windowStart), eligibleIds);
        Map<String, Integer> favorites = countMap(favoriteRepository.countByUserSince(windowStart), eligibleIds);
        Map<String, UserFeedbackAggregate> feedback = feedbackRepository.aggregateByUserSince(windowStart).stream()
                .filter(row -> eligibleIds.contains(row.getUserId()))
                .collect(Collectors.toMap(UserFeedbackAggregate::getUserId, Function.identity()));
        Map<String, Map<String, Integer>> categories = categoryMap(
                registrationRepository.countApprovedCategoriesByUserSince(windowStart), eligibleIds
        );

        List<FeatureSample> samples = new ArrayList<>(users.size());
        for (User user : users) {
            UserRegistrationAggregate registration = registrations.get(user.getId());
            UserFeedbackAggregate userFeedback = feedback.get(user.getId());
            samples.add(new FeatureSample(
                    user.getId(),
                    sortedDistinct(user.getInterests()),
                    normalizedProfileValue(user.getCollege()),
                    normalizedProfileValue(user.getGrade()),
                    sortedDistinct(user.getAvailableTime()),
                    registration == null ? 0 : checkedInt(registration.getTotalCount()),
                    registration == null ? 0 : checkedInt(registration.getApprovedCount()),
                    favorites.getOrDefault(user.getId(), 0),
                    checkIns.getOrDefault(user.getId(), 0),
                    userFeedback == null ? 0 : checkedInt(userFeedback.getTotalCount()),
                    userFeedback == null ? null : userFeedback.getAverageRating(),
                    categories.getOrDefault(user.getId(), Map.of())
            ));
        }

        Map<String, Object> manifest = manifest(users, categories.values());
        int featureDimension = NUMERIC_FEATURES.size()
                + listSize(manifest, "colleges")
                + listSize(manifest, "grades")
                + listSize(manifest, "interests")
                + listSize(manifest, "availableTimes")
                + listSize(manifest, "activityCategories");
        return new CommunityFeatureSnapshot(
                FEATURE_SCHEMA_VERSION, windowStart, samples, manifest, featureDimension
        );
    }

    private static Map<String, Integer> countMap(
            Collection<? extends UserBehaviorCount> rows,
            Set<String> eligibleIds
    ) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (UserBehaviorCount row : rows) {
            if (eligibleIds.contains(row.getUserId())) {
                result.put(row.getUserId(), checkedInt(row.getTotalCount()));
            }
        }
        return result;
    }

    private static Map<String, Map<String, Integer>> categoryMap(
            List<UserCategoryCount> rows,
            Set<String> eligibleIds
    ) {
        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();
        for (UserCategoryCount row : rows) {
            if (eligibleIds.contains(row.getUserId())) {
                result.computeIfAbsent(row.getUserId(), ignored -> new TreeMap<>())
                        .put(row.getCategory(), checkedInt(row.getTotalCount()));
            }
        }
        return result;
    }

    private static Map<String, Object> manifest(
            List<User> users,
            Collection<Map<String, Integer>> categoryCounts
    ) {
        TreeSet<String> colleges = new TreeSet<>();
        TreeSet<String> grades = new TreeSet<>();
        TreeSet<String> interests = new TreeSet<>();
        TreeSet<String> availableTimes = new TreeSet<>();
        TreeSet<String> activityCategories = new TreeSet<>();
        for (User user : users) {
            colleges.add(normalizedProfileValue(user.getCollege()));
            grades.add(normalizedProfileValue(user.getGrade()));
            interests.addAll(sortedDistinct(user.getInterests()));
            availableTimes.addAll(sortedDistinct(user.getAvailableTime()));
        }
        categoryCounts.forEach(counts -> activityCategories.addAll(counts.keySet()));

        LinkedHashMap<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", FEATURE_SCHEMA_VERSION);
        manifest.put("windowDays", WINDOW_DAYS);
        manifest.put("numericFeatures", NUMERIC_FEATURES);
        manifest.put("colleges", List.copyOf(colleges));
        manifest.put("grades", List.copyOf(grades));
        manifest.put("interests", List.copyOf(interests));
        manifest.put("availableTimes", List.copyOf(availableTimes));
        manifest.put("activityCategories", List.copyOf(activityCategories));
        manifest.put("groupWeights", Map.of("college", 0.35, "grade", 0.35));
        manifest.put("excludedRoles", List.of("admin"));
        manifest.put("usesActivityView", false);
        return manifest;
    }

    private static List<String> sortedDistinct(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }

    private static String normalizedProfileValue(String value) {
        return value == null || value.isBlank() ? "<missing>" : value.trim();
    }

    private static int checkedInt(Long value) {
        if (value == null || value < 0 || value > Integer.MAX_VALUE) {
            throw new ClusteringStateException(ClusteringStateException.Code.INVALID_PARAMETERS);
        }
        return value.intValue();
    }

    @SuppressWarnings("unchecked")
    private static int listSize(Map<String, Object> manifest, String key) {
        return ((List<String>) manifest.get(key)).size();
    }
}
