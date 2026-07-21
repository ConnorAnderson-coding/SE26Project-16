package com.example.campusactivity.controller;

import com.example.campusactivity.client.clustering.ClusteringClient;
import com.example.campusactivity.dto.ApiResponse;
import com.example.campusactivity.dto.clustering.ClusteringRunSummaryResponse;
import com.example.campusactivity.dto.clustering.CurrentUserClusteringResponse;
import com.example.campusactivity.dto.clustering.LatestClusteringResponse;
import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.repository.ClusteringRunRepository;
import com.example.campusactivity.repository.CommunityMemberRepository;
import com.example.campusactivity.repository.CommunityRepository;
import com.example.campusactivity.repository.UserRepository;
import com.example.campusactivity.service.auth.AuthService;
import com.example.campusactivity.service.clustering.ClusteringQueryCode;
import com.example.campusactivity.service.clustering.ClusteringQueryException;
import com.example.campusactivity.service.clustering.CommunityClusteringOrchestrator;
import com.example.campusactivity.service.clustering.CommunityClusteringQueryService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CommunityClusteringControllerTest {
    @Test
    void latestForwardsAuthenticationNameUnchangedAndReturnsSameDto() {
        CommunityClusteringQueryService queryService =
                mock(CommunityClusteringQueryService.class);
        Authentication authentication = mock(Authentication.class);
        CommunityClusteringController controller =
                new CommunityClusteringController(queryService);
        String currentUserId = "  opaque-current-user  ";
        LatestClusteringResponse response = latestResponse();
        when(authentication.getName()).thenReturn(currentUserId);
        when(queryService.findLatestClustering(currentUserId))
                .thenReturn(response);

        LatestClusteringResponse actual = controller.latest(authentication);

        assertThat(actual).isSameAs(response);
        verify(authentication).getName();
        verify(queryService).findLatestClustering(currentUserId);
        verifyNoMoreInteractions(authentication, queryService);
    }

    @Test
    void meForwardsAuthenticationNameUnchangedAndReturnsSameDto() {
        CommunityClusteringQueryService queryService =
                mock(CommunityClusteringQueryService.class);
        Authentication authentication = mock(Authentication.class);
        CommunityClusteringController controller =
                new CommunityClusteringController(queryService);
        String currentUserId = "  opaque-current-user  ";
        CurrentUserClusteringResponse response =
                new CurrentUserClusteringResponse("run-1", "version-1", null);
        when(authentication.getName()).thenReturn(currentUserId);
        when(queryService.findCurrentUserClustering(currentUserId))
                .thenReturn(response);

        CurrentUserClusteringResponse actual = controller.me(authentication);

        assertThat(actual).isSameAs(response);
        verify(authentication).getName();
        verify(queryService).findCurrentUserClustering(currentUserId);
        verifyNoMoreInteractions(authentication, queryService);
    }

    @Test
    void letsQueryExceptionsPropagateUnchanged() {
        CommunityClusteringQueryService queryService =
                mock(CommunityClusteringQueryService.class);
        Authentication authentication = mock(Authentication.class);
        CommunityClusteringController controller =
                new CommunityClusteringController(queryService);
        ClusteringQueryException failure = new ClusteringQueryException(
                ClusteringQueryCode.NO_SUCCESSFUL_RUN
        );
        when(authentication.getName()).thenReturn("current-user");
        when(queryService.findLatestClustering("current-user"))
                .thenThrow(failure);

        assertThatThrownBy(() -> controller.latest(authentication))
                .isSameAs(failure);
        verify(authentication).getName();
        verify(queryService).findLatestClustering("current-user");
        verifyNoMoreInteractions(authentication, queryService);
    }

    @Test
    void controllerHasOnlyApprovedDependencyAndTwoExactBusinessMethods() {
        Class<CommunityClusteringController> type =
                CommunityClusteringController.class;
        List<Field> instanceFields = Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        List<Method> publicMethods = Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();

        assertThat(instanceFields)
                .extracting(Field::getType)
                .containsExactly(CommunityClusteringQueryService.class)
                .doesNotContain(
                        ClusteringRunRepository.class,
                        CommunityRepository.class,
                        CommunityMemberRepository.class,
                        UserRepository.class,
                        AuthService.class,
                        CommunityClusteringOrchestrator.class,
                        ClusteringClient.class,
                        EntityManager.class
                );
        assertThat(publicMethods).hasSize(2);
        assertBusinessMethod(
                type,
                "latest",
                "/latest",
                LatestClusteringResponse.class
        );
        assertBusinessMethod(
                type,
                "me",
                "/me",
                CurrentUserClusteringResponse.class
        );
        assertThat(publicMethods)
                .extracting(Method::getReturnType)
                .doesNotContain(ResponseEntity.class, ApiResponse.class);
    }

    @Test
    void controllerAndBothAdvicesArePreciselyScopedAndUnconditional() {
        Class<CommunityClusteringController> controller =
                CommunityClusteringController.class;
        RequestMapping requestMapping =
                controller.getAnnotation(RequestMapping.class);
        RestControllerAdvice userAdvice =
                CommunityClusteringUserQueryExceptionHandler.class
                        .getAnnotation(RestControllerAdvice.class);
        RestControllerAdvice adminAdvice =
                CommunityClusteringQueryExceptionHandler.class
                        .getAnnotation(RestControllerAdvice.class);

        assertThat(controller).hasAnnotation(RestController.class);
        assertThat(requestMapping.value())
                .containsExactly("/api/v1/community-clustering");
        assertThat(controller.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(annotationNames(controller))
                .noneMatch(name -> name.contains("Conditional"));
        assertThat(annotationNames(
                CommunityClusteringUserQueryExceptionHandler.class
        )).noneMatch(name -> name.contains("Conditional"));
        assertThat(userAdvice.assignableTypes())
                .containsExactly(CommunityClusteringController.class);
        assertThat(adminAdvice.assignableTypes())
                .containsExactly(AdminCommunityClusteringController.class);
    }

    private static void assertBusinessMethod(
            Class<CommunityClusteringController> type,
            String methodName,
            String path,
            Class<?> returnType
    ) {
        Method method;
        try {
            method = type.getDeclaredMethod(methodName, Authentication.class);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
        assertThat(method.getReturnType()).isEqualTo(returnType);
        assertThat(method.getParameterTypes())
                .containsExactly(Authentication.class);
        assertThat(method.getParameters())
                .extracting(parameter -> parameter.getName().toLowerCase())
                .noneMatch(name -> name.contains("userid"));
        assertThat(method.getAnnotation(GetMapping.class).value())
                .containsExactly(path);
        assertThat(method.isAnnotationPresent(Transactional.class)).isFalse();
    }

    private static Set<String> annotationNames(Class<?> type) {
        return Arrays.stream(type.getAnnotations())
                .map(annotation -> annotation.annotationType().getName())
                .collect(Collectors.toSet());
    }

    private static LatestClusteringResponse latestResponse() {
        return new LatestClusteringResponse(
                new ClusteringRunSummaryResponse(
                        "run-1",
                        "version-1",
                        ClusteringAlgorithm.KMEANS,
                        2,
                        2,
                        Instant.parse("2026-07-21T01:00:00Z")
                ),
                List.of()
        );
    }
}
