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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityFeatureBuilderTest {

    private static final Instant NOW = Instant.parse("2026-07-23T08:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private RegistrationRepository registrationRepository;
    @Mock
    private CheckInRepository checkInRepository;
    @Mock
    private FavoriteRepository favoriteRepository;
    @Mock
    private FeedbackRepository feedbackRepository;

    private CommunityFeatureBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new CommunityFeatureBuilder(
                userRepository,
                registrationRepository,
                checkInRepository,
                favoriteRepository,
                feedbackRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void buildsStableMainEntityFeaturesAndRetainsColdStartUsers() {
        User first = user("student-1", "student", " 电院 ", "2024", List.of("AI", "摄影", "AI"),
                List.of("weekend", "weekday_evening"));
        User coldStart = user("teacher-1", "teacher", "", "2020", null, List.of());
        when(userRepository.findByRoleInOrderByIdAsc(Set.of("student", "teacher")))
                .thenReturn(List.of(first, coldStart));

        LocalDateTime cutoff = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(180);
        UserRegistrationAggregate studentRegistrations = registrations("student-1", 5L, 3L);
        UserRegistrationAggregate adminRegistrations = mock(UserRegistrationAggregate.class);
        when(adminRegistrations.getUserId()).thenReturn("admin-1");
        UserBehaviorCount studentCheckIns = count("student-1", 2L);
        UserBehaviorCount studentFavorites = count("student-1", 4L);
        UserFeedbackAggregate studentFeedback = feedback("student-1", 2L, 4.5);
        UserCategoryCount sports = category("student-1", "sports", 1L);
        UserCategoryCount academic = category("student-1", "academic", 2L);
        UserCategoryCount ignoredAdmin = mock(UserCategoryCount.class);
        when(ignoredAdmin.getUserId()).thenReturn("admin-1");
        when(registrationRepository.aggregateByUserSince(cutoff))
                .thenReturn(List.of(studentRegistrations, adminRegistrations));
        when(checkInRepository.countByUserSince(cutoff)).thenReturn(List.of(studentCheckIns));
        when(favoriteRepository.countByUserSince(cutoff)).thenReturn(List.of(studentFavorites));
        when(feedbackRepository.aggregateByUserSince(cutoff)).thenReturn(List.of(studentFeedback));
        when(registrationRepository.countApprovedCategoriesByUserSince(cutoff))
                .thenReturn(List.of(sports, academic, ignoredAdmin));

        CommunityFeatureSnapshot snapshot = builder.build();

        assertThat(snapshot.schemaVersion()).isEqualTo("community-features-v2");
        assertThat(snapshot.windowStart()).isEqualTo(cutoff);
        assertThat(snapshot.samples()).extracting(FeatureSample::userId)
                .containsExactly("student-1", "teacher-1");
        FeatureSample populated = snapshot.samples().getFirst();
        assertThat(populated.interests()).containsExactly("AI", "摄影");
        assertThat(populated.college()).isEqualTo("电院");
        assertThat(populated.signupCount()).isEqualTo(5);
        assertThat(populated.approvedSignupCount()).isEqualTo(3);
        assertThat(populated.favoriteCount()).isEqualTo(4);
        assertThat(populated.checkInCount()).isEqualTo(2);
        assertThat(populated.feedbackCount()).isEqualTo(2);
        assertThat(populated.averageRating()).isEqualTo(4.5);
        assertThat(populated.categoryParticipationCounts()).containsExactlyInAnyOrderEntriesOf(
                Map.of("academic", 2, "sports", 1)
        );

        FeatureSample cold = snapshot.samples().get(1);
        assertThat(cold.college()).isEqualTo("<missing>");
        assertThat(cold.interests()).isEmpty();
        assertThat(cold.signupCount()).isZero();
        assertThat(cold.averageRating()).isNull();
        assertThat(cold.categoryParticipationCounts()).isEmpty();

        assertThat(snapshot.manifest())
                .containsEntry("windowDays", 180)
                .containsEntry("usesActivityView", false)
                .containsEntry("excludedRoles", List.of("admin"));
        assertThat(snapshot.manifest().get("activityCategories"))
                .isEqualTo(List.of("academic", "sports"));
        assertThat(snapshot.featureDimension()).isEqualTo(18);

        verify(registrationRepository).aggregateByUserSince(cutoff);
        verify(registrationRepository).countApprovedCategoriesByUserSince(cutoff);
        verify(checkInRepository).countByUserSince(cutoff);
        verify(favoriteRepository).countByUserSince(cutoff);
        verify(feedbackRepository).aggregateByUserSince(cutoff);
    }

    private static User user(
            String id,
            String role,
            String college,
            String grade,
            List<String> interests,
            List<String> availableTime
    ) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setCollege(college);
        user.setGrade(grade);
        user.setInterests(interests);
        user.setAvailableTime(availableTime);
        return user;
    }

    private static UserBehaviorCount count(String userId, Long total) {
        UserBehaviorCount row = mock(UserBehaviorCount.class);
        when(row.getUserId()).thenReturn(userId);
        when(row.getTotalCount()).thenReturn(total);
        return row;
    }

    private static UserRegistrationAggregate registrations(String userId, Long total, Long approved) {
        UserRegistrationAggregate row = mock(UserRegistrationAggregate.class);
        when(row.getUserId()).thenReturn(userId);
        when(row.getTotalCount()).thenReturn(total);
        when(row.getApprovedCount()).thenReturn(approved);
        return row;
    }

    private static UserFeedbackAggregate feedback(String userId, Long total, Double average) {
        UserFeedbackAggregate row = mock(UserFeedbackAggregate.class);
        when(row.getUserId()).thenReturn(userId);
        when(row.getTotalCount()).thenReturn(total);
        when(row.getAverageRating()).thenReturn(average);
        return row;
    }

    private static UserCategoryCount category(String userId, String name, Long total) {
        UserCategoryCount row = mock(UserCategoryCount.class);
        when(row.getUserId()).thenReturn(userId);
        when(row.getCategory()).thenReturn(name);
        when(row.getTotalCount()).thenReturn(total);
        return row;
    }
}
