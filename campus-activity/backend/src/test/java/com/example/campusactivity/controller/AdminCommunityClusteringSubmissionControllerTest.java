package com.example.campusactivity.controller;

import com.example.campusactivity.dto.clustering.TriggerClusteringRequest;
import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.service.clustering.ClusteringSubmissionResult;
import com.example.campusactivity.service.clustering.CommunityClusteringSubmissionFacade;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCommunityClusteringSubmissionControllerTest {
    @Test
    void returnsAcceptedPendingLocationAndUsesOnlyPrincipalName() {
        TriggerClusteringRequestParser parser = mock(TriggerClusteringRequestParser.class);
        CommunityClusteringSubmissionFacade facade = mock(
                CommunityClusteringSubmissionFacade.class
        );
        Authentication authentication = mock(Authentication.class);
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        when(parser.parse(body)).thenReturn(new TriggerClusteringRequest(null));
        when(authentication.getName()).thenReturn("authenticated-admin");
        when(facade.submit(2, "authenticated-admin")).thenReturn(submission());
        AdminCommunityClusteringSubmissionController controller =
                new AdminCommunityClusteringSubmissionController(parser, facade);

        var response = controller.trigger(body, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getLocation()).hasToString(
                "/api/v1/admin/community-clustering/runs/run-1"
        );
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(ClusteringRunStatus.PENDING);
        verify(facade).submit(2, "authenticated-admin");
    }

    private static ClusteringSubmissionResult submission() {
        return new ClusteringSubmissionResult(
                "run-1",
                "version-1",
                ClusteringAlgorithm.KMEANS,
                2,
                42,
                ClusteringRunStatus.PENDING,
                Instant.parse("2026-07-21T00:00:00Z")
        );
    }
}
