package com.example.campusactivity.controller;

import com.example.campusactivity.client.clustering.ClusteringClient;
import com.example.campusactivity.dto.clustering.ClusteringRunDetailResponse;
import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.repository.ClusteringRunRepository;
import com.example.campusactivity.service.clustering.ClusteringQueryCode;
import com.example.campusactivity.service.clustering.ClusteringQueryException;
import com.example.campusactivity.service.clustering.CommunityClusteringOrchestrator;
import com.example.campusactivity.service.clustering.CommunityClusteringQueryService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AdminCommunityClusteringControllerTest {
    @Test
    void forwardsRunIdUnchangedAndReturnsSameDtoInstance() {
        CommunityClusteringQueryService queryService =
                mock(CommunityClusteringQueryService.class);
        AdminCommunityClusteringController controller =
                new AdminCommunityClusteringController(queryService);
        String runId = "  opaque-run-id  ";
        ClusteringRunDetailResponse response = response(runId);
        when(queryService.findRunById(runId)).thenReturn(response);

        ClusteringRunDetailResponse actual = controller.findRun(runId);

        assertThat(actual).isSameAs(response);
        verify(queryService).findRunById(runId);
        verifyNoMoreInteractions(queryService);
    }

    @Test
    void letsQueryExceptionsPropagateUnchanged() {
        CommunityClusteringQueryService queryService =
                mock(CommunityClusteringQueryService.class);
        AdminCommunityClusteringController controller =
                new AdminCommunityClusteringController(queryService);
        ClusteringQueryException failure =
                new ClusteringQueryException(ClusteringQueryCode.RUN_NOT_FOUND);
        when(queryService.findRunById("missing-run")).thenThrow(failure);

        assertThatThrownBy(() -> controller.findRun("missing-run"))
                .isSameAs(failure);
        verify(queryService).findRunById("missing-run");
        verifyNoMoreInteractions(queryService);
    }

    @Test
    void controllerHasOnlyTheApprovedDependencyAndOneBusinessGetMethod() {
        Class<AdminCommunityClusteringController> type =
                AdminCommunityClusteringController.class;
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
                        CommunityClusteringOrchestrator.class,
                        ClusteringClient.class,
                        EntityManager.class
                );
        assertThat(publicMethods).singleElement().satisfies(method -> {
            assertThat(method.getName()).isEqualTo("findRun");
            assertThat(method.getReturnType())
                    .isEqualTo(ClusteringRunDetailResponse.class);
            assertThat(method.getReturnType()).isNotEqualTo(ResponseEntity.class);
            assertThat(method.isAnnotationPresent(GetMapping.class)).isTrue();
            assertThat(method.isAnnotationPresent(Transactional.class)).isFalse();
        });
    }

    @Test
    void controllerAndAdviceArePreciselyScopedAndUnconditional() {
        Class<AdminCommunityClusteringController> controller =
                AdminCommunityClusteringController.class;
        RequestMapping requestMapping =
                controller.getAnnotation(RequestMapping.class);
        RestControllerAdvice advice =
                CommunityClusteringQueryExceptionHandler.class.getAnnotation(
                        RestControllerAdvice.class
                );

        assertThat(controller).hasAnnotation(RestController.class);
        assertThat(requestMapping.value())
                .containsExactly("/api/v1/admin/community-clustering/runs");
        assertThat(controller.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(Arrays.stream(controller.getAnnotations())
                .map(annotation -> annotation.annotationType().getName()))
                .noneMatch(name -> name.contains("Conditional"));
        assertThat(advice.assignableTypes())
                .containsExactly(AdminCommunityClusteringController.class);
        assertThat(Arrays.stream(
                        CommunityClusteringQueryExceptionHandler.class
                                .getAnnotations())
                .map(annotation -> annotation.annotationType().getName()))
                .noneMatch(name -> name.contains("Conditional"));
    }

    private static ClusteringRunDetailResponse response(String runId) {
        return new ClusteringRunDetailResponse(
                runId,
                "version-1",
                ClusteringAlgorithm.KMEANS,
                2,
                42,
                ClusteringRunStatus.PENDING,
                null,
                "community-features-v1",
                null,
                null,
                Instant.parse("2026-07-20T01:00:00Z"),
                null,
                null,
                "admin-1"
        );
    }
}
