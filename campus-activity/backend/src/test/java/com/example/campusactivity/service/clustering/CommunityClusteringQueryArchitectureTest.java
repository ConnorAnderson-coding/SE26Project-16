package com.example.campusactivity.service.clustering;

import com.example.campusactivity.dto.clustering.ClusteringFailureResponse;
import com.example.campusactivity.dto.clustering.AdminCommunityMemberResponse;
import com.example.campusactivity.dto.clustering.AdminCommunitySummaryResponse;
import com.example.campusactivity.dto.clustering.CommunityMembersPageResponse;
import com.example.campusactivity.dto.clustering.ClusteringMetricsResponse;
import com.example.campusactivity.dto.clustering.ClusteringRunDetailResponse;
import com.example.campusactivity.dto.clustering.ClusteringRunSummaryResponse;
import com.example.campusactivity.dto.clustering.CommunityMemberPointResponse;
import com.example.campusactivity.dto.clustering.CommunityResponse;
import com.example.campusactivity.dto.clustering.CurrentUserClusteringResponse;
import com.example.campusactivity.dto.clustering.CurrentUserMembershipResponse;
import com.example.campusactivity.dto.clustering.LatestClusteringResponse;
import com.example.campusactivity.dto.clustering.TriggerClusteringResponse;
import com.example.campusactivity.entity.ClusteringRun;
import com.example.campusactivity.entity.Community;
import com.example.campusactivity.entity.CommunityMember;
import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.ClusteringRunRepository;
import com.example.campusactivity.repository.CommunityMemberRepository;
import com.example.campusactivity.repository.CommunityRepository;
import com.example.campusactivity.repository.projection.ClusteringRunQueryProjection;
import com.example.campusactivity.repository.projection.CommunityMemberPointProjection;
import com.example.campusactivity.repository.projection.CommunityQueryProjection;
import com.example.campusactivity.repository.projection.CurrentUserMembershipProjection;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommunityClusteringQueryArchitectureTest {
    private static final List<Class<?>> DTO_TYPES = List.of(
            ClusteringMetricsResponse.class,
            ClusteringFailureResponse.class,
            ClusteringRunSummaryResponse.class,
            ClusteringRunDetailResponse.class,
            CommunityMemberPointResponse.class,
            CommunityResponse.class,
            LatestClusteringResponse.class,
            CurrentUserMembershipResponse.class,
            CurrentUserClusteringResponse.class,
            TriggerClusteringResponse.class
    );

    private static final List<Class<?>> PROJECTION_TYPES = List.of(
            ClusteringRunQueryProjection.class,
            CommunityQueryProjection.class,
            CommunityMemberPointProjection.class,
            CurrentUserMembershipProjection.class
    );

    @Test
    void queryServiceIsUnconditionalReadOnlyServiceWithOnlyApprovedDependencies() {
        Class<CommunityClusteringQueryService> type =
                CommunityClusteringQueryService.class;
        Transactional transactional = type.getAnnotation(Transactional.class);

        assertThat(type).hasAnnotation(Service.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
        assertThat(Arrays.stream(type.getAnnotations())
                .map(annotation -> annotation.annotationType().getName()))
                .noneMatch(name -> name.contains("Conditional"));

        List<Field> instanceFields = Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        assertThat(instanceFields)
                .extracting(Field::getType)
                .containsExactlyInAnyOrder(
                        ClusteringRunRepository.class,
                        CommunityRepository.class,
                        CommunityMemberRepository.class,
                        ClusteringStoredJsonParser.class
                );
        assertThat(instanceFields).hasSize(4);
    }

    @Test
    void queryServiceHasOnlyApprovedPublicBusinessMethods() {
        Set<String> publicMethods = Arrays.stream(
                        CommunityClusteringQueryService.class.getDeclaredMethods()
                )
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(publicMethods).containsExactlyInAnyOrder(
                "findRunById",
                "findRuns",
                "findCommunityMembers",
                "findLatestClustering",
                "findCurrentUserClustering"
        );
    }

    @Test
    void dtoShapesDoNotReferenceJpaAggregatesOrExposePrivateIdentifiers() {
        Set<Class<?>> forbiddenTypes = Set.of(
                ClusteringRun.class,
                Community.class,
                CommunityMember.class,
                UserAccount.class
        );
        for (Class<?> dtoType : DTO_TYPES) {
            assertThat(dtoType.isRecord()).as(dtoType.getSimpleName()).isTrue();
            assertThat(Arrays.stream(dtoType.getRecordComponents())
                    .map(component -> component.getType()))
                    .as(dtoType.getSimpleName())
                    .noneMatch(forbiddenTypes::contains);
            assertThat(Arrays.stream(dtoType.getRecordComponents())
                    .map(component -> component.getName()))
                    .as(dtoType.getSimpleName())
                    .doesNotContain(
                            "userId",
                            "user",
                            "parametersJson",
                            "metricsJson",
                            "topInterestsJson",
                            "errorMessage",
                            "activeSlot",
                            "college",
                            "grade"
                    );
        }

        assertThat(recordComponentNames(CommunityMemberPointResponse.class))
                .containsExactly("pointId", "x", "y", "currentUser")
                .doesNotContain("communityId", "distanceToCenter", "assignedAt");
    }

    @Test
    void projectionsExposeNoUserIdEntitiesOrInternalRunParameters() {
        for (Class<?> projection : PROJECTION_TYPES) {
            assertThat(projection.isInterface()).isTrue();
            assertThat(Arrays.stream(projection.getMethods())
                    .map(Method::getName))
                    .doesNotContain("getUserId", "getUser");
            assertThat(Arrays.stream(projection.getMethods())
                    .map(Method::getReturnType))
                    .doesNotContain(
                            ClusteringRun.class,
                            Community.class,
                            CommunityMember.class,
                            UserAccount.class
                    );
        }
        assertThat(Arrays.stream(ClusteringRunQueryProjection.class.getMethods())
                .map(Method::getName))
                .doesNotContain("getActiveSlot", "getParametersJson");
    }

    @Test
    void adminMemberDtosExposeOnlyTheApprovedPrivateAdminShape() {
        assertThat(recordComponentNames(AdminCommunityMemberResponse.class))
                .containsExactly(
                        "userId", "name", "college", "grade", "pointId",
                        "x", "y", "distanceToCenter"
                )
                .doesNotContain(
                        "password", "passwordHash", "role", "authorities",
                        "friends", "interests", "assignedAt"
                );
        assertThat(recordComponentNames(AdminCommunitySummaryResponse.class))
                .containsExactly(
                        "communityId", "runId", "clusterNo", "name", "color",
                        "memberCount"
                );
        assertThat(recordComponentNames(CommunityMembersPageResponse.class))
                .containsExactly(
                        "community", "items", "page", "size",
                        "totalElements", "totalPages"
                );
        assertThat(Arrays.stream(AdminCommunityMemberResponse.class.getRecordComponents())
                .map(component -> component.getType()))
                .noneMatch(type -> type == UserAccount.class
                        || type == CommunityMember.class);
    }

    @Test
    void adminMemberQueryUsesOnePagedJoinAndStableApprovedSorting()
            throws Exception {
        Query query = CommunityMemberRepository.class
                .getMethod(
                        "findAdminMembersByCommunityId",
                        String.class,
                        org.springframework.data.domain.Pageable.class
                )
                .getAnnotation(Query.class);

        assertThat(query.value())
                .contains(
                        "JOIN member.user user",
                        "member.communityId = :communityId",
                        "ORDER BY member.distanceToCenter ASC, user.id ASC"
                )
                .doesNotContain(
                        "user.password", "user.role", "user.interests",
                        "user.friends", "FETCH"
                );
    }

    @Test
    void pointQueriesDeriveCurrentUserButNeverSelectUserId() throws Exception {
        Query pointsQuery = CommunityMemberRepository.class
                .getMethod(
                        "findPointProjectionsByRunId",
                        String.class,
                        String.class
                )
                .getAnnotation(Query.class);
        Query membershipQuery = CommunityMemberRepository.class
                .getMethod("findMembershipProjection", String.class, String.class)
                .getAnnotation(Query.class);

        assertThat(pointsQuery.value())
                .contains(
                        "CASE WHEN member.user.id = :currentUserId THEN true ELSE false END"
                )
                .doesNotContain("AS userId", "FETCH");
        assertThat(membershipQuery.value())
                .contains(
                        "member.run.id = :runId",
                        "member.user.id = :currentUserId"
                )
                .doesNotContain("AS userId", "FETCH");
    }

    @Test
    void latestRunQueryUsesBoundedStableSuccessSelection() throws Exception {
        Query query = ClusteringRunRepository.class
                .getMethod(
                        "findSuccessfulQueryProjectionsForLatest",
                        org.springframework.data.domain.Pageable.class
                )
                .getAnnotation(Query.class);

        assertThat(query.value())
                .contains(
                        "ClusteringRunStatus.SUCCESS",
                        "run.finishedAt IS NOT NULL",
                        "ORDER BY run.finishedAt DESC, run.createdAt DESC, run.id DESC"
                )
                .doesNotContain("activeSlot", "parametersJson");
    }

    @Test
    void responseCollectionsAreDefensivelyCopied() {
        List<Double> ratios = new java.util.ArrayList<>(List.of(0.7, 0.2));
        ClusteringMetricsResponse metrics = new ClusteringMetricsResponse(1.0, ratios);
        ratios.add(0.1);
        assertThat(metrics.pcaExplainedVarianceRatio()).containsExactly(0.7, 0.2);
        assertThatThrownBy(() -> metrics.pcaExplainedVarianceRatio().add(0.1))
                .isInstanceOf(UnsupportedOperationException.class);

        List<String> interests = new java.util.ArrayList<>(List.of("AI"));
        List<CommunityMemberPointResponse> points = new java.util.ArrayList<>(
                List.of(new CommunityMemberPointResponse("point", 1.0, 2.0, false))
        );
        CommunityResponse community = new CommunityResponse(
                "community",
                0,
                "社区",
                null,
                1,
                interests,
                "#1677FF",
                points
        );
        interests.add("编程");
        points.clear();
        assertThat(community.topInterests()).containsExactly("AI");
        assertThat(community.points()).hasSize(1);
        assertThatThrownBy(() -> community.topInterests().add("新增"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> community.points().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        List<CommunityResponse> communities = new java.util.ArrayList<>(List.of(community));
        LatestClusteringResponse latest = new LatestClusteringResponse(
                new ClusteringRunSummaryResponse(
                        "run",
                        "version",
                        com.example.campusactivity.entity.ClusteringAlgorithm.KMEANS,
                        2,
                        2,
                        Instant.parse("2026-07-17T00:00:00Z")
                ),
                communities
        );
        communities.clear();
        assertThat(latest.communities()).hasSize(1);
        assertThatThrownBy(() -> latest.communities().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void everyQueryExceptionUsesOnlyFixedMessageAndNullCause() {
        for (ClusteringQueryCode code : ClusteringQueryCode.values()) {
            ClusteringQueryException exception = new ClusteringQueryException(code);
            assertThat(exception.getCode()).isEqualTo(code);
            assertThat(exception.getMessage()).isEqualTo(code.safeMessage());
            assertThat(exception.getCause()).isNull();
            assertThat(exception.getSuppressed()).isEmpty();
            assertThat(exception.toString())
                    .contains(code.safeMessage())
                    .doesNotContain(
                            "run-secret",
                            "user-secret",
                            "{\"secret\"",
                            "SQLException"
                    );
        }
    }

    private static List<String> recordComponentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(component -> component.getName())
                .toList();
    }
}
