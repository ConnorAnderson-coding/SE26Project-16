package com.example.campusactivity.service.clustering;

import com.example.campusactivity.client.clustering.dto.FeatureSample;
import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.CheckInRepository;
import com.example.campusactivity.repository.FavoriteRepository;
import com.example.campusactivity.repository.FeedbackRepository;
import com.example.campusactivity.repository.SignupRepository;
import com.example.campusactivity.repository.UserRepository;
import com.example.campusactivity.repository.projection.ApprovedSignupCategoryProjection;
import com.example.campusactivity.repository.projection.FeedbackAggregateProjection;
import com.example.campusactivity.repository.projection.SignupCountProjection;
import com.example.campusactivity.repository.projection.UserCollectionValueProjection;
import com.example.campusactivity.repository.projection.UserCountProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Service
public class CommunityFeatureAggregationService {
    private static final String APPROVED_STATUS = "approved";
    private static final List<String> KNOWN_SIGNUP_STATUSES = List.of(
            APPROVED_STATUS,
            "pending",
            "rejected"
    );
    private static final int MINIMUM_RATING = 1;
    private static final int MAXIMUM_RATING = 5;
    private static final long MAXIMUM_FEATURE_COUNT = Integer.MAX_VALUE;

    private static final Comparator<ExcludedUserDiagnostic> EXCLUDED_USER_ORDER = Comparator
            .comparing(
                    ExcludedUserDiagnostic::userId,
                    Comparator.nullsFirst(Comparator.naturalOrder())
            )
            .thenComparing(ExcludedUserDiagnostic::code)
            .thenComparing(ExcludedUserDiagnostic::message);

    private final UserRepository userRepository;
    private final SignupRepository signupRepository;
    private final FavoriteRepository favoriteRepository;
    private final CheckInRepository checkInRepository;
    private final FeedbackRepository feedbackRepository;

    public CommunityFeatureAggregationService(
            UserRepository userRepository,
            SignupRepository signupRepository,
            FavoriteRepository favoriteRepository,
            CheckInRepository checkInRepository,
            FeedbackRepository feedbackRepository
    ) {
        this.userRepository = userRepository;
        this.signupRepository = signupRepository;
        this.favoriteRepository = favoriteRepository;
        this.checkInRepository = checkInRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional(readOnly = true)
    public FeatureAggregationResult aggregateFeatureSamples() {
        List<UserAccount> users = userRepository.findAllByOrderByIdAsc();
        List<UserCollectionValueProjection> interestRows = userRepository.findAllInterestValues();
        List<UserCollectionValueProjection> availableTimeRows = userRepository.findAllAvailableTimeValues();
        List<SignupCountProjection> signupRows = signupRepository.aggregateCountsByUserId(
                APPROVED_STATUS,
                KNOWN_SIGNUP_STATUSES
        );
        List<ApprovedSignupCategoryProjection> categoryRows =
                signupRepository.aggregateApprovedCategoriesByUserId(APPROVED_STATUS);
        List<UserCountProjection> favoriteRows = favoriteRepository.aggregateCountsByUserId();
        List<UserCountProjection> checkInRows = checkInRepository.aggregateCountsByUserId();
        List<FeedbackAggregateProjection> feedbackRows = feedbackRepository.aggregateByUserId(
                MINIMUM_RATING,
                MAXIMUM_RATING
        );

        Map<String, List<String>> interestsByUser = collectionValuesByUser(interestRows);
        Map<String, List<String>> availableTimesByUser = collectionValuesByUser(availableTimeRows);
        Map<String, SignupCounts> signupCountsByUser = signupCountsByUser(signupRows);
        Map<String, Long> favoriteCountsByUser = userCountsByUser(favoriteRows);
        Map<String, Long> checkInCountsByUser = userCountsByUser(checkInRows);
        Map<String, FeedbackCounts> feedbackCountsByUser = feedbackCountsByUser(feedbackRows);
        Map<String, CategoryCounts> categoryCountsByUser = categoryCountsByUser(categoryRows);

        List<ExcludedUserDiagnostic> excludedUsers = new ArrayList<>();
        List<UserProfile> candidates = new ArrayList<>();
        long invalidUserCount = 0L;
        long excludedAdminCount = 0L;

        for (UserAccount user : users) {
            String userId = user.getId();
            if (!isValidUserId(userId)) {
                invalidUserCount = Math.addExact(invalidUserCount, 1L);
                excludedUsers.add(new ExcludedUserDiagnostic(
                        userId,
                        "INVALID_USER_ID",
                        "用户ID为空、空白或长度超过64"
                ));
                continue;
            }
            if (isAdmin(user.getRole())) {
                excludedAdminCount = Math.addExact(excludedAdminCount, 1L);
                excludedUsers.add(new ExcludedUserDiagnostic(
                        userId,
                        "ADMIN_EXCLUDED",
                        "管理员不纳入社区聚类样本"
                ));
                continue;
            }
            candidates.add(new UserProfile(
                    userId,
                    normalizeCollection(interestsByUser.get(userId)),
                    normalizeLabel(user.getCollege()),
                    normalizeLabel(user.getGrade()),
                    normalizeCollection(availableTimesByUser.get(userId))
            ));
        }

        List<UserProfile> includedUsers = new ArrayList<>();
        long countOverflowUserCount = 0L;
        for (UserProfile user : candidates) {
            String userId = user.userId();
            SignupCounts signupCounts = signupCountsByUser.getOrDefault(userId, SignupCounts.ZERO);
            long favoriteCount = favoriteCountsByUser.getOrDefault(userId, 0L);
            long checkInCount = checkInCountsByUser.getOrDefault(userId, 0L);
            FeedbackCounts feedbackCounts = feedbackCountsByUser.getOrDefault(userId, FeedbackCounts.ZERO);
            CategoryCounts categoryCounts = categoryCountsByUser.getOrDefault(userId, CategoryCounts.EMPTY);

            if (hasFeatureCountOverflow(
                    signupCounts,
                    favoriteCount,
                    checkInCount,
                    feedbackCounts,
                    categoryCounts
            )) {
                countOverflowUserCount = Math.addExact(countOverflowUserCount, 1L);
                excludedUsers.add(new ExcludedUserDiagnostic(
                        userId,
                        "COUNT_OVERFLOW",
                        "用户特征计数超过int32范围"
                ));
                continue;
            }
            includedUsers.add(user);
        }

        Set<String> includedUserIds = new HashSet<>();
        for (UserProfile user : includedUsers) {
            includedUserIds.add(user.userId());
        }

        long ignoredOrphanBehaviorCount = ignoredBehaviorCount(
                includedUserIds,
                signupCountsByUser,
                favoriteCountsByUser,
                checkInCountsByUser,
                feedbackCountsByUser
        );
        long missingActivityCount = 0L;
        long blankActivityCategoryCount = 0L;
        long invalidRatingCount = 0L;
        long unknownSignupStatusCount = 0L;
        TreeSet<String> categoryUniverse = new TreeSet<>();

        for (String userId : includedUserIds) {
            CategoryCounts categories = categoryCountsByUser.getOrDefault(userId, CategoryCounts.EMPTY);
            missingActivityCount = Math.addExact(missingActivityCount, categories.missingActivityCount());
            blankActivityCategoryCount = Math.addExact(
                    blankActivityCategoryCount,
                    categories.blankActivityCategoryCount()
            );
            categoryUniverse.addAll(categories.counts().keySet());

            FeedbackCounts feedback = feedbackCountsByUser.getOrDefault(userId, FeedbackCounts.ZERO);
            invalidRatingCount = Math.addExact(invalidRatingCount, feedback.invalidRatingCount());

            SignupCounts signup = signupCountsByUser.getOrDefault(userId, SignupCounts.ZERO);
            unknownSignupStatusCount = Math.addExact(
                    unknownSignupStatusCount,
                    signup.unknownSignupStatusCount()
            );
        }

        List<FeatureSample> samples = new ArrayList<>();
        for (UserProfile user : includedUsers) {
            String userId = user.userId();
            SignupCounts signup = signupCountsByUser.getOrDefault(userId, SignupCounts.ZERO);
            FeedbackCounts feedback = feedbackCountsByUser.getOrDefault(userId, FeedbackCounts.ZERO);
            CategoryCounts categories = categoryCountsByUser.getOrDefault(userId, CategoryCounts.EMPTY);

            Map<String, Integer> participationCounts = new LinkedHashMap<>();
            for (String category : categoryUniverse) {
                participationCounts.put(
                        category,
                        toFeatureCount(categories.counts().getOrDefault(category, 0L))
                );
            }

            samples.add(new FeatureSample(
                    userId,
                    user.interests(),
                    user.college(),
                    user.grade(),
                    user.availableTime(),
                    toFeatureCount(signup.signupCount()),
                    toFeatureCount(signup.approvedSignupCount()),
                    toFeatureCount(favoriteCountsByUser.getOrDefault(userId, 0L)),
                    toFeatureCount(checkInCountsByUser.getOrDefault(userId, 0L)),
                    toFeatureCount(feedback.feedbackCount()),
                    averageRating(feedback),
                    participationCounts
            ));
        }

        samples.sort(Comparator.comparing(FeatureSample::userId));
        excludedUsers.sort(EXCLUDED_USER_ORDER);

        return new FeatureAggregationResult(
                samples,
                excludedUsers,
                new FeatureAggregationDiagnostics(
                        excludedAdminCount,
                        invalidUserCount,
                        countOverflowUserCount,
                        ignoredOrphanBehaviorCount,
                        missingActivityCount,
                        blankActivityCategoryCount,
                        invalidRatingCount,
                        unknownSignupStatusCount
                )
        );
    }

    private static Map<String, List<String>> collectionValuesByUser(
            Collection<UserCollectionValueProjection> rows
    ) {
        Map<String, List<String>> valuesByUser = new HashMap<>();
        for (UserCollectionValueProjection row : rows) {
            valuesByUser.computeIfAbsent(row.getUserId(), ignored -> new ArrayList<>())
                    .add(row.getCollectionValue());
        }
        return valuesByUser;
    }

    private static Map<String, SignupCounts> signupCountsByUser(
            Collection<SignupCountProjection> rows
    ) {
        Map<String, SignupCounts> countsByUser = new HashMap<>();
        for (SignupCountProjection row : rows) {
            SignupCounts counts = new SignupCounts(
                    requiredCount(row.getSignupCount(), "signupCount"),
                    requiredCount(row.getApprovedSignupCount(), "approvedSignupCount"),
                    requiredCount(row.getUnknownSignupStatusCount(), "unknownSignupStatusCount")
            );
            countsByUser.merge(row.getUserId(), counts, SignupCounts::plus);
        }
        return countsByUser;
    }

    private static Map<String, Long> userCountsByUser(Collection<UserCountProjection> rows) {
        Map<String, Long> countsByUser = new HashMap<>();
        for (UserCountProjection row : rows) {
            long count = requiredCount(row.getRecordCount(), "recordCount");
            countsByUser.merge(row.getUserId(), count, Math::addExact);
        }
        return countsByUser;
    }

    private static Map<String, FeedbackCounts> feedbackCountsByUser(
            Collection<FeedbackAggregateProjection> rows
    ) {
        Map<String, FeedbackCounts> countsByUser = new HashMap<>();
        for (FeedbackAggregateProjection row : rows) {
            FeedbackCounts counts = new FeedbackCounts(
                    requiredCount(row.getFeedbackCount(), "feedbackCount"),
                    requiredCount(row.getValidRatingCount(), "validRatingCount"),
                    requiredCount(row.getValidRatingSum(), "validRatingSum"),
                    requiredCount(row.getInvalidRatingCount(), "invalidRatingCount")
            );
            countsByUser.merge(row.getUserId(), counts, FeedbackCounts::plus);
        }
        return countsByUser;
    }

    private static Map<String, CategoryCounts> categoryCountsByUser(
            Collection<ApprovedSignupCategoryProjection> rows
    ) {
        Map<String, MutableCategoryCounts> mutableCounts = new HashMap<>();
        for (ApprovedSignupCategoryProjection row : rows) {
            long count = requiredCount(row.getParticipationCount(), "participationCount");
            MutableCategoryCounts userCounts = mutableCounts.computeIfAbsent(
                    row.getUserId(),
                    ignored -> new MutableCategoryCounts()
            );
            if (row.getMatchedActivityId() == null) {
                userCounts.missingActivityCount = Math.addExact(userCounts.missingActivityCount, count);
                continue;
            }
            String category = normalizeLabel(row.getCategory());
            if (category == null) {
                userCounts.blankActivityCategoryCount = Math.addExact(
                        userCounts.blankActivityCategoryCount,
                        count
                );
                continue;
            }
            userCounts.counts.merge(category, count, Math::addExact);
        }

        Map<String, CategoryCounts> result = new HashMap<>();
        for (Map.Entry<String, MutableCategoryCounts> entry : mutableCounts.entrySet()) {
            MutableCategoryCounts counts = entry.getValue();
            result.put(entry.getKey(), new CategoryCounts(
                    Map.copyOf(counts.counts),
                    counts.missingActivityCount,
                    counts.blankActivityCategoryCount
            ));
        }
        return result;
    }

    private static boolean hasFeatureCountOverflow(
            SignupCounts signup,
            long favoriteCount,
            long checkInCount,
            FeedbackCounts feedback,
            CategoryCounts categories
    ) {
        if (!isFeatureCount(signup.signupCount())
                || !isFeatureCount(signup.approvedSignupCount())
                || !isFeatureCount(favoriteCount)
                || !isFeatureCount(checkInCount)
                || !isFeatureCount(feedback.feedbackCount())) {
            return true;
        }
        for (long categoryCount : categories.counts().values()) {
            if (!isFeatureCount(categoryCount)) {
                return true;
            }
        }
        return false;
    }

    private static long ignoredBehaviorCount(
            Set<String> includedUserIds,
            Map<String, SignupCounts> signups,
            Map<String, Long> favorites,
            Map<String, Long> checkIns,
            Map<String, FeedbackCounts> feedbacks
    ) {
        long count = 0L;
        for (Map.Entry<String, SignupCounts> entry : signups.entrySet()) {
            if (!includedUserIds.contains(entry.getKey())) {
                count = Math.addExact(count, entry.getValue().signupCount());
            }
        }
        count = Math.addExact(count, ignoredUserCounts(includedUserIds, favorites));
        count = Math.addExact(count, ignoredUserCounts(includedUserIds, checkIns));
        for (Map.Entry<String, FeedbackCounts> entry : feedbacks.entrySet()) {
            if (!includedUserIds.contains(entry.getKey())) {
                count = Math.addExact(count, entry.getValue().feedbackCount());
            }
        }
        return count;
    }

    private static long ignoredUserCounts(Set<String> includedUserIds, Map<String, Long> counts) {
        long ignoredCount = 0L;
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            if (!includedUserIds.contains(entry.getKey())) {
                ignoredCount = Math.addExact(ignoredCount, entry.getValue());
            }
        }
        return ignoredCount;
    }

    private static List<String> normalizeCollection(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (String value : values) {
            String normalizedValue = normalizeLabel(value);
            if (normalizedValue != null) {
                normalized.add(normalizedValue);
            }
        }
        return List.copyOf(normalized);
    }

    private static String normalizeLabel(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static boolean isValidUserId(String userId) {
        return userId != null && !userId.isBlank() && userId.length() <= 64;
    }

    private static boolean isAdmin(String role) {
        return role != null && "admin".equals(role.strip().toLowerCase(Locale.ROOT));
    }

    private static boolean isFeatureCount(long count) {
        return count >= 0L && count <= MAXIMUM_FEATURE_COUNT;
    }

    private static int toFeatureCount(long count) {
        if (!isFeatureCount(count)) {
            throw new IllegalArgumentException("特征计数超出int32范围");
        }
        return (int) count;
    }

    private static long requiredCount(Long count, String field) {
        long value = Objects.requireNonNull(count, field + " 不能为空");
        if (value < 0L) {
            throw new IllegalArgumentException(field + " 不能为负数");
        }
        return value;
    }

    private static Double averageRating(FeedbackCounts feedback) {
        if (feedback.validRatingCount() == 0L) {
            return null;
        }
        return feedback.validRatingSum() / (double) feedback.validRatingCount();
    }

    private record UserProfile(
            String userId,
            List<String> interests,
            String college,
            String grade,
            List<String> availableTime
    ) {
    }

    private record SignupCounts(
            long signupCount,
            long approvedSignupCount,
            long unknownSignupStatusCount
    ) {
        private static final SignupCounts ZERO = new SignupCounts(0L, 0L, 0L);

        private SignupCounts plus(SignupCounts other) {
            return new SignupCounts(
                    Math.addExact(signupCount, other.signupCount),
                    Math.addExact(approvedSignupCount, other.approvedSignupCount),
                    Math.addExact(unknownSignupStatusCount, other.unknownSignupStatusCount)
            );
        }
    }

    private record FeedbackCounts(
            long feedbackCount,
            long validRatingCount,
            long validRatingSum,
            long invalidRatingCount
    ) {
        private static final FeedbackCounts ZERO = new FeedbackCounts(0L, 0L, 0L, 0L);

        private FeedbackCounts plus(FeedbackCounts other) {
            return new FeedbackCounts(
                    Math.addExact(feedbackCount, other.feedbackCount),
                    Math.addExact(validRatingCount, other.validRatingCount),
                    Math.addExact(validRatingSum, other.validRatingSum),
                    Math.addExact(invalidRatingCount, other.invalidRatingCount)
            );
        }
    }

    private record CategoryCounts(
            Map<String, Long> counts,
            long missingActivityCount,
            long blankActivityCategoryCount
    ) {
        private static final CategoryCounts EMPTY = new CategoryCounts(Map.of(), 0L, 0L);
    }

    private static final class MutableCategoryCounts {
        private final Map<String, Long> counts = new HashMap<>();
        private long missingActivityCount;
        private long blankActivityCategoryCount;
    }
}
