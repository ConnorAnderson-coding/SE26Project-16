package com.example.campusactivity.service.clustering;

import com.example.campusactivity.client.clustering.ClusteringClient;
import com.example.campusactivity.client.clustering.dto.FeatureSample;
import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.ActivityRepository;
import com.example.campusactivity.repository.CheckInRepository;
import com.example.campusactivity.repository.ClusteringRunRepository;
import com.example.campusactivity.repository.CommunityMemberRepository;
import com.example.campusactivity.repository.CommunityRepository;
import com.example.campusactivity.repository.FavoriteRepository;
import com.example.campusactivity.repository.FeedbackRepository;
import com.example.campusactivity.repository.SignupRepository;
import com.example.campusactivity.repository.UserRepository;
import com.example.campusactivity.repository.projection.ApprovedSignupCategoryProjection;
import com.example.campusactivity.repository.projection.FeedbackAggregateProjection;
import com.example.campusactivity.repository.projection.SignupCountProjection;
import com.example.campusactivity.repository.projection.UserCollectionValueProjection;
import com.example.campusactivity.repository.projection.UserCountProjection;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class CommunityFeatureAggregationServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private SignupRepository signupRepository;
    @Mock
    private FavoriteRepository favoriteRepository;
    @Mock
    private CheckInRepository checkInRepository;
    @Mock
    private FeedbackRepository feedbackRepository;

    private CommunityFeatureAggregationService service;

    @BeforeEach
    void setUp() {
        service = new CommunityFeatureAggregationService(
                userRepository,
                signupRepository,
                favoriteRepository,
                checkInRepository,
                feedbackRepository
        );
        lenient().when(userRepository.findAllByOrderByIdAsc()).thenReturn(List.of());
        lenient().when(userRepository.findAllInterestValues()).thenReturn(List.of());
        lenient().when(userRepository.findAllAvailableTimeValues()).thenReturn(List.of());
        lenient().when(signupRepository.aggregateCountsByUserId(
                        "approved",
                        List.of("approved", "pending", "rejected")
                ))
                .thenReturn(List.of());
        lenient().when(signupRepository.aggregateApprovedCategoriesByUserId("approved")).thenReturn(List.of());
        lenient().when(favoriteRepository.aggregateCountsByUserId()).thenReturn(List.of());
        lenient().when(checkInRepository.aggregateCountsByUserId()).thenReturn(List.of());
        lenient().when(feedbackRepository.aggregateByUserId(1, 5)).thenReturn(List.of());
    }

    @Test
    void includesNonAdminsNormalizesProfilesPreservesUserIdAndUsesOnlyBulkQueries() {
        UserAccount admin = user("admin", "  AdMiN  ", null, null);
        UserAccount teacher = user("  T001  ", "teacher", "　软件学院　", "２０２４级");
        UserAccount student = user("student", "student", "   ", null);
        when(userRepository.findAllByOrderByIdAsc()).thenReturn(List.of(student, admin, teacher));
        when(userRepository.findAllInterestValues()).thenReturn(List.of(
                collection("  T001  ", " ＡＩ "),
                collection("  T001  ", "AI"),
                collection("  T001  ", "　"),
                collection("  T001  ", null)
        ));
        when(userRepository.findAllAvailableTimeValues()).thenReturn(List.of(
                collection("  T001  ", " weekend "),
                collection("  T001  ", "weekday_evening"),
                collection("  T001  ", "weekend")
        ));

        FeatureAggregationResult result = service.aggregateFeatureSamples();

        assertThat(result.samples()).extracting(FeatureSample::userId)
                .containsExactly("  T001  ", "student");
        FeatureSample teacherSample = sample(result, "  T001  ");
        assertThat(teacherSample.userId()).isEqualTo("  T001  ");
        assertThat(teacherSample.interests()).containsExactly("AI");
        assertThat(teacherSample.college()).isEqualTo("软件学院");
        assertThat(teacherSample.grade()).isEqualTo("2024级");
        assertThat(teacherSample.availableTime()).containsExactly("weekday_evening", "weekend");
        assertThat(teacherSample.signupCount()).isZero();
        assertThat(teacherSample.averageRating()).isNull();
        assertThat(sample(result, "student").interests()).isEmpty();
        assertThat(sample(result, "student").college()).isNull();
        assertThat(result.diagnostics().excludedAdminCount()).isEqualTo(1L);
        assertThat(result.excludedUsers()).extracting(ExcludedUserDiagnostic::code)
                .containsExactly("ADMIN_EXCLUDED");

        verify(userRepository, times(1)).findAllByOrderByIdAsc();
        verify(userRepository, times(1)).findAllInterestValues();
        verify(userRepository, times(1)).findAllAvailableTimeValues();
        verify(signupRepository, times(1))
                .aggregateCountsByUserId("approved", List.of("approved", "pending", "rejected"));
        verify(signupRepository, times(1)).aggregateApprovedCategoriesByUserId("approved");
        verify(favoriteRepository, times(1)).aggregateCountsByUserId();
        verify(checkInRepository, times(1)).aggregateCountsByUserId();
        verify(feedbackRepository, times(1)).aggregateByUserId(1, 5);
        verify(signupRepository, never()).findByUserId(anyString());
        verify(favoriteRepository, never()).findByUserId(anyString());
        verify(checkInRepository, never()).findByUserId(anyString());
        verify(feedbackRepository, never()).findByUserId(anyString());
    }

    @Test
    void aggregatesAllFeaturesBuildsApprovedCategoryUnionAndRecordsDiagnostics() throws Exception {
        when(userRepository.findAllByOrderByIdAsc()).thenReturn(List.of(
                user("u2", "organizer", null, null),
                user("u1", "student", null, null)
        ));
        when(signupRepository.aggregateCountsByUserId("approved", List.of("approved", "pending", "rejected")))
                .thenReturn(List.of(
                        signupCount("u1", 8L, 5L, 2L),
                        signupCount("u2", 1L, 1L, 0L)
                ));
        when(signupRepository.aggregateApprovedCategoriesByUserId("approved")).thenReturn(List.of(
                category("u1", "a2", " ｓｐｏｒｔｓ ", 1L),
                category("u1", "a1", "academic", 2L),
                category("u1", null, null, 1L),
                category("u1", "blank", "　", 1L),
                category("u2", "a3", "club", 1L)
        ));
        when(favoriteRepository.aggregateCountsByUserId()).thenReturn(List.of(
                userCount("u2", 1L),
                userCount("u1", 2L)
        ));
        when(checkInRepository.aggregateCountsByUserId()).thenReturn(List.of(userCount("u1", 3L)));
        when(feedbackRepository.aggregateByUserId(1, 5)).thenReturn(List.of(
                feedback("u1", 4L, 2L, 9L, 1L)
        ));

        FeatureAggregationResult result = service.aggregateFeatureSamples();

        assertThat(result.samples()).extracting(FeatureSample::userId).containsExactly("u1", "u2");
        FeatureSample first = sample(result, "u1");
        assertThat(first.signupCount()).isEqualTo(8);
        assertThat(first.approvedSignupCount()).isEqualTo(5);
        assertThat(first.favoriteCount()).isEqualTo(2);
        assertThat(first.checkInCount()).isEqualTo(3);
        assertThat(first.feedbackCount()).isEqualTo(4);
        assertThat(first.averageRating()).isEqualTo(4.5);
        assertThat(first.categoryParticipationCounts().keySet())
                .containsExactly("academic", "club", "sports");
        assertThat(first.categoryParticipationCounts())
                .containsEntry("academic", 2)
                .containsEntry("club", 0)
                .containsEntry("sports", 1);
        Map<String, Integer> secondCategories = sample(result, "u2").categoryParticipationCounts();
        assertThat(secondCategories.keySet()).containsExactly("academic", "club", "sports");
        assertThat(secondCategories)
                .containsEntry("academic", 0)
                .containsEntry("club", 1)
                .containsEntry("sports", 0);
        assertThat(result.diagnostics().missingActivityCount()).isEqualTo(1L);
        assertThat(result.diagnostics().blankActivityCategoryCount()).isEqualTo(1L);
        assertThat(result.diagnostics().invalidRatingCount()).isEqualTo(1L);
        assertThat(result.diagnostics().unknownSignupStatusCount()).isEqualTo(2L);

        String json = new ObjectMapper().writeValueAsString(first);
        assertThat(json.indexOf("\"academic\"")).isLessThan(json.indexOf("\"club\""));
        assertThat(json.indexOf("\"club\"")).isLessThan(json.indexOf("\"sports\""));
    }

    @Test
    void excludesInvalidAndOverflowUsersWithoutClampingAndCountsTheirBehaviorAsIgnored() {
        UserAccount nullId = user(null, "student", null, null);
        UserAccount blankId = user("   ", "student", null, null);
        UserAccount admin = user("admin", "ADMIN", null, null);
        UserAccount maximum = user("maximum", "teacher", null, null);
        UserAccount overflow = user("overflow", "student", null, null);
        UserAccount zero = user("zero", null, null, null);
        when(userRepository.findAllByOrderByIdAsc()).thenReturn(List.of(
                overflow,
                admin,
                zero,
                blankId,
                maximum,
                nullId
        ));
        when(signupRepository.aggregateCountsByUserId("approved", List.of("approved", "pending", "rejected")))
                .thenReturn(List.of(
                        signupCount("maximum", (long) Integer.MAX_VALUE, 0L, 0L),
                        signupCount("overflow", 1L, 0L, 0L),
                        signupCount("admin", 2L, 0L, 0L),
                        signupCount("   ", 1L, 0L, 0L),
                        signupCount("missing", 3L, 0L, 0L)
                ));
        when(favoriteRepository.aggregateCountsByUserId()).thenReturn(List.of(
                userCount("overflow", (long) Integer.MAX_VALUE + 1L)
        ));

        FeatureAggregationResult result = service.aggregateFeatureSamples();

        assertThat(result.samples()).extracting(FeatureSample::userId)
                .containsExactly("maximum", "zero");
        assertThat(sample(result, "maximum").signupCount()).isEqualTo(Integer.MAX_VALUE);
        assertThat(sample(result, "zero").averageRating()).isNull();
        assertThat(result.diagnostics().invalidUserCount()).isEqualTo(2L);
        assertThat(result.diagnostics().excludedAdminCount()).isEqualTo(1L);
        assertThat(result.diagnostics().countOverflowUserCount()).isEqualTo(1L);
        assertThat(result.diagnostics().ignoredOrphanBehaviorCount())
                .isEqualTo((long) Integer.MAX_VALUE + 8L);
        assertThat(result.excludedUsers()).extracting(ExcludedUserDiagnostic::code)
                .containsExactly("INVALID_USER_ID", "INVALID_USER_ID", "ADMIN_EXCLUDED", "COUNT_OVERFLOW");
    }

    @Test
    void shuffledRepositoryRowsProduceEqualResults() {
        UserAccount first = user("u1", "student", null, null);
        UserAccount second = user("u2", "teacher", null, null);
        when(userRepository.findAllByOrderByIdAsc())
                .thenReturn(List.of(second, first), List.of(first, second));
        when(userRepository.findAllInterestValues()).thenReturn(
                List.of(collection("u1", "摄影"), collection("u1", "AI")),
                List.of(collection("u1", "AI"), collection("u1", "摄影"))
        );
        when(signupRepository.aggregateCountsByUserId("approved", List.of("approved", "pending", "rejected")))
                .thenReturn(
                        List.of(signupCount("u2", 1L, 1L, 0L), signupCount("u1", 2L, 1L, 0L)),
                        List.of(signupCount("u1", 2L, 1L, 0L), signupCount("u2", 1L, 1L, 0L))
                );
        when(signupRepository.aggregateApprovedCategoriesByUserId("approved")).thenReturn(
                List.of(category("u2", "a2", "sports", 1L), category("u1", "a1", "academic", 1L)),
                List.of(category("u1", "a1", "academic", 1L), category("u2", "a2", "sports", 1L))
        );

        FeatureAggregationResult firstResult = service.aggregateFeatureSamples();
        FeatureAggregationResult secondResult = service.aggregateFeatureSamples();

        assertThat(secondResult).isEqualTo(firstResult);
    }

    @Test
    void serviceBoundaryIsReadOnlyAndHasNoPythonActivityOrClusteringResultDependencies() throws Exception {
        Method aggregate = CommunityFeatureAggregationService.class.getMethod("aggregateFeatureSamples");
        Transactional transactional = aggregate.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
        assertThat(transactional.isolation()).isEqualTo(Isolation.DEFAULT);

        List<String> fieldTypeNames = Arrays.stream(
                        CommunityFeatureAggregationService.class.getDeclaredFields()
                )
                .map(field -> field.getType().getName())
                .toList();
        List<String> forbiddenDependencyNames = List.of(
                ClusteringClient.class.getName(),
                ActivityRepository.class.getName(),
                ClusteringRunRepository.class.getName(),
                CommunityRepository.class.getName(),
                CommunityMemberRepository.class.getName()
        );
        assertThat(Collections.disjoint(fieldTypeNames, forbiddenDependencyNames)).isTrue();
    }

    private static FeatureSample sample(FeatureAggregationResult result, String userId) {
        return result.samples().stream()
                .filter(sample -> sample.userId().equals(userId))
                .findFirst()
                .orElseThrow();
    }

    private static UserAccount user(String id, String role, String college, String grade) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setRole(role);
        user.setCollege(college);
        user.setGrade(grade);
        return user;
    }

    private static UserCollectionValueProjection collection(String userId, String value) {
        return new CollectionRow(userId, value);
    }

    private static SignupCountProjection signupCount(
            String userId,
            long signupCount,
            long approvedCount,
            long unknownCount
    ) {
        return new SignupRow(userId, signupCount, approvedCount, unknownCount);
    }

    private static ApprovedSignupCategoryProjection category(
            String userId,
            String activityId,
            String category,
            long count
    ) {
        return new CategoryRow(userId, activityId, category, count);
    }

    private static UserCountProjection userCount(String userId, long count) {
        return new UserCountRow(userId, count);
    }

    private static FeedbackAggregateProjection feedback(
            String userId,
            long count,
            long validCount,
            long validSum,
            long invalidCount
    ) {
        return new FeedbackRow(userId, count, validCount, validSum, invalidCount);
    }

    private record CollectionRow(String userId, String collectionValue)
            implements UserCollectionValueProjection {
        @Override
        public String getUserId() {
            return userId;
        }

        @Override
        public String getCollectionValue() {
            return collectionValue;
        }
    }

    private record SignupRow(
            String userId,
            Long signupCount,
            Long approvedSignupCount,
            Long unknownSignupStatusCount
    ) implements SignupCountProjection {
        @Override
        public String getUserId() {
            return userId;
        }

        @Override
        public Long getSignupCount() {
            return signupCount;
        }

        @Override
        public Long getApprovedSignupCount() {
            return approvedSignupCount;
        }

        @Override
        public Long getUnknownSignupStatusCount() {
            return unknownSignupStatusCount;
        }
    }

    private record CategoryRow(
            String userId,
            String matchedActivityId,
            String category,
            Long participationCount
    ) implements ApprovedSignupCategoryProjection {
        @Override
        public String getUserId() {
            return userId;
        }

        @Override
        public String getMatchedActivityId() {
            return matchedActivityId;
        }

        @Override
        public String getCategory() {
            return category;
        }

        @Override
        public Long getParticipationCount() {
            return participationCount;
        }
    }

    private record UserCountRow(String userId, Long recordCount) implements UserCountProjection {
        @Override
        public String getUserId() {
            return userId;
        }

        @Override
        public Long getRecordCount() {
            return recordCount;
        }
    }

    private record FeedbackRow(
            String userId,
            Long feedbackCount,
            Long validRatingCount,
            Long validRatingSum,
            Long invalidRatingCount
    ) implements FeedbackAggregateProjection {
        @Override
        public String getUserId() {
            return userId;
        }

        @Override
        public Long getFeedbackCount() {
            return feedbackCount;
        }

        @Override
        public Long getValidRatingCount() {
            return validRatingCount;
        }

        @Override
        public Long getValidRatingSum() {
            return validRatingSum;
        }

        @Override
        public Long getInvalidRatingCount() {
            return invalidRatingCount;
        }
    }
}
