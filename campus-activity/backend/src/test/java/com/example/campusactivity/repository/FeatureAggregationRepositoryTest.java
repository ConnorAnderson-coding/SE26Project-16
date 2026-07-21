package com.example.campusactivity.repository;

import com.example.campusactivity.entity.Activity;
import com.example.campusactivity.entity.CheckInRecord;
import com.example.campusactivity.entity.Favorite;
import com.example.campusactivity.entity.Feedback;
import com.example.campusactivity.entity.Signup;
import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.projection.ApprovedSignupCategoryProjection;
import com.example.campusactivity.repository.projection.FeedbackAggregateProjection;
import com.example.campusactivity.repository.projection.SignupCountProjection;
import com.example.campusactivity.repository.projection.UserCollectionValueProjection;
import com.example.campusactivity.repository.projection.UserCountProjection;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class FeatureAggregationRepositoryTest {
    private static final List<String> KNOWN_STATUSES = List.of("approved", "pending", "rejected");

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private SignupRepository signupRepository;
    @Autowired
    private FavoriteRepository favoriteRepository;
    @Autowired
    private CheckInRepository checkInRepository;
    @Autowired
    private FeedbackRepository feedbackRepository;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        userRepository.save(user(
                "u2",
                List.of("摄影", "AI", "AI"),
                List.of("weekend", "weekday_evening")
        ));
        userRepository.save(user("u1", List.of(), List.of()));

        activityRepository.save(activity("a-approved", "academic", 999, 888));
        activityRepository.save(activity("a-blank", "　", 777, 666));

        signupRepository.save(signup("s1", "u1", "a-approved", "approved"));
        signupRepository.save(signup("s2", "u1", "a-approved", "approved"));
        signupRepository.save(signup("s3", "u1", "a-approved", "APPROVED"));
        signupRepository.save(signup("s4", "u1", "a-approved", "pending"));
        signupRepository.save(signup("s5", "u1", "a-approved", null));
        signupRepository.save(signup("s6", "u1", "a-approved", "mystery"));
        signupRepository.save(signup("s7", "u1", "missing-activity", "approved"));
        signupRepository.save(signup("s8", "u1", "a-blank", "approved"));
        signupRepository.save(signup("s-orphan", "missing-user", "a-approved", "pending"));

        favoriteRepository.save(favorite("fav1", "u1", "a-approved"));
        favoriteRepository.save(favorite("fav2", "u1", "a-approved"));
        favoriteRepository.save(favorite("fav-orphan", "missing-user", "missing-activity"));

        checkInRepository.save(checkIn("c1", "u1", "missing-activity"));
        checkInRepository.save(checkIn("c-orphan", "missing-user", "missing-activity"));

        feedbackRepository.save(feedback("f1", "u1", 5));
        feedbackRepository.save(feedback("f2", "u1", 4));
        feedbackRepository.save(feedback("f3", "u1", null));
        feedbackRepository.save(feedback("f4", "u1", 0));
        feedbackRepository.save(feedback("f5", "u1", 6));
        feedbackRepository.save(feedback("f-orphan", "missing-user", 5));

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void readsUsersAndListElementCollectionsWithSeparateBulkQueries() {
        assertThat(userRepository.findAllByOrderByIdAsc())
                .extracting(UserAccount::getId)
                .containsExactly("u1", "u2");

        List<UserCollectionValueProjection> interests = userRepository.findAllInterestValues();
        assertThat(interests)
                .filteredOn(row -> row.getUserId().equals("u2"))
                .extracting(UserCollectionValueProjection::getCollectionValue)
                .containsExactlyInAnyOrder("摄影", "AI", "AI");

        List<UserCollectionValueProjection> availableTimes = userRepository.findAllAvailableTimeValues();
        assertThat(availableTimes)
                .filteredOn(row -> row.getUserId().equals("u2"))
                .extracting(UserCollectionValueProjection::getCollectionValue)
                .containsExactlyInAnyOrder("weekend", "weekday_evening");
    }

    @Test
    void aggregatesSignupCountsStatusesDuplicatesAndOrphanUsers() {
        List<SignupCountProjection> rows = signupRepository.aggregateCountsByUserId(
                "approved",
                KNOWN_STATUSES
        );

        SignupCountProjection user = signupCounts(rows, "u1");
        assertThat(user.getSignupCount()).isEqualTo(8L);
        assertThat(user.getApprovedSignupCount()).isEqualTo(4L);
        assertThat(user.getUnknownSignupStatusCount()).isEqualTo(3L);
        assertThat(signupCounts(rows, "missing-user").getSignupCount()).isEqualTo(1L);
    }

    @Test
    void leftJoinsApprovedSignupsToActivitiesWithoutUsingDisplayCounts() {
        List<ApprovedSignupCategoryProjection> rows =
                signupRepository.aggregateApprovedCategoriesByUserId("approved");

        ApprovedSignupCategoryProjection academic = rows.stream()
                .filter(row -> "a-approved".equals(row.getMatchedActivityId()))
                .findFirst()
                .orElseThrow();
        assertThat(academic.getCategory()).isEqualTo("academic");
        assertThat(academic.getParticipationCount()).isEqualTo(2L);

        ApprovedSignupCategoryProjection missing = rows.stream()
                .filter(row -> row.getMatchedActivityId() == null)
                .findFirst()
                .orElseThrow();
        assertThat(missing.getParticipationCount()).isEqualTo(1L);

        ApprovedSignupCategoryProjection blank = rows.stream()
                .filter(row -> "a-blank".equals(row.getMatchedActivityId()))
                .findFirst()
                .orElseThrow();
        assertThat(blank.getCategory()).isEqualTo("　");
        assertThat(blank.getParticipationCount()).isEqualTo(1L);

        assertThat(rows).noneMatch(row -> "APPROVED".equals(row.getCategory()));
        assertThat(academic.getParticipationCount()).isNotEqualTo(999L);
    }

    @Test
    void aggregatesFavoriteAndCheckInRowsIncludingLogicalDuplicatesAndOrphans() {
        List<UserCountProjection> favorites = favoriteRepository.aggregateCountsByUserId();
        assertThat(userCount(favorites, "u1")).isEqualTo(2L);
        assertThat(userCount(favorites, "missing-user")).isEqualTo(1L);

        List<UserCountProjection> checkIns = checkInRepository.aggregateCountsByUserId();
        assertThat(userCount(checkIns, "u1")).isEqualTo(1L);
        assertThat(userCount(checkIns, "missing-user")).isEqualTo(1L);
    }

    @Test
    void aggregatesFeedbackCountValidRatingSumAndNonNullInvalidRatings() {
        List<FeedbackAggregateProjection> rows = feedbackRepository.aggregateByUserId(1, 5);

        FeedbackAggregateProjection user = feedbackCounts(rows, "u1");
        assertThat(user.getFeedbackCount()).isEqualTo(5L);
        assertThat(user.getValidRatingCount()).isEqualTo(2L);
        assertThat(user.getValidRatingSum()).isEqualTo(9L);
        assertThat(user.getInvalidRatingCount()).isEqualTo(2L);
        assertThat(feedbackCounts(rows, "missing-user").getFeedbackCount()).isEqualTo(1L);
    }

    private static SignupCountProjection signupCounts(List<SignupCountProjection> rows, String userId) {
        return rows.stream().filter(row -> userId.equals(row.getUserId())).findFirst().orElseThrow();
    }

    private static FeedbackAggregateProjection feedbackCounts(
            List<FeedbackAggregateProjection> rows,
            String userId
    ) {
        return rows.stream().filter(row -> userId.equals(row.getUserId())).findFirst().orElseThrow();
    }

    private static long userCount(List<UserCountProjection> rows, String userId) {
        return rows.stream()
                .filter(row -> userId.equals(row.getUserId()))
                .map(UserCountProjection::getRecordCount)
                .findFirst()
                .orElseThrow();
    }

    private static UserAccount user(String id, List<String> interests, List<String> availableTime) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setPassword("password");
        user.setName("测试用户");
        user.setInterests(interests);
        user.setAvailableTime(availableTime);
        return user;
    }

    private static Activity activity(String id, String category, int signupCount, int favoriteCount) {
        Activity activity = new Activity();
        activity.setId(id);
        activity.setTitle("测试活动");
        activity.setCategory(category);
        activity.setSignupCount(signupCount);
        activity.setFavoriteCount(favoriteCount);
        return activity;
    }

    private static Signup signup(String id, String userId, String activityId, String status) {
        Signup signup = new Signup();
        signup.setId(id);
        signup.setUserId(userId);
        signup.setActivityId(activityId);
        signup.setStatus(status);
        return signup;
    }

    private static Favorite favorite(String id, String userId, String activityId) {
        Favorite favorite = new Favorite();
        favorite.setId(id);
        favorite.setUserId(userId);
        favorite.setActivityId(activityId);
        return favorite;
    }

    private static CheckInRecord checkIn(String id, String userId, String activityId) {
        CheckInRecord checkIn = new CheckInRecord();
        checkIn.setId(id);
        checkIn.setUserId(userId);
        checkIn.setActivityId(activityId);
        checkIn.setMethod("test");
        return checkIn;
    }

    private static Feedback feedback(String id, String userId, Integer rating) {
        Feedback feedback = new Feedback();
        feedback.setId(id);
        feedback.setUserId(userId);
        feedback.setActivityId("missing-activity");
        feedback.setUserName("测试用户");
        feedback.setRating(rating);
        feedback.setContent("测试评价");
        return feedback;
    }
}
