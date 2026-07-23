package com.example.demo.community.service;

import com.example.demo.community.client.ClusteringClient;
import com.example.demo.community.client.ClusteringClientException;
import com.example.demo.community.client.ClusteringContracts;
import com.example.demo.community.client.ClusteringContracts.CommunitySummary;
import com.example.demo.community.client.ClusteringContracts.FeatureSample;
import com.example.demo.community.client.ClusteringContracts.MemberResult;
import com.example.demo.community.client.ClusteringContracts.Metrics;
import com.example.demo.community.client.ClusteringContracts.Request;
import com.example.demo.community.client.ClusteringContracts.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityClusteringWorkerTest {

    @Mock
    private ClusteringRequestFactory requestFactory;

    @Mock
    private ClusteringClient client;

    @Mock
    private ClusteringRunLifecycleService lifecycleService;

    @InjectMocks
    private CommunityClusteringWorker worker;

    @Test
    void completesRunAfterSuccessfulRemoteCall() {
        Request request = request();
        Response response = response();
        when(requestFactory.buildRequest("run-1")).thenReturn(request);
        when(client.runClustering(request)).thenReturn(response);

        worker.execute("run-1");

        verify(lifecycleService).complete("run-1", response);
        verify(lifecycleService, never()).markFailed("run-1", "INTERNAL_ERROR", "聚类运行执行失败");
    }

    @Test
    void convergesRemoteFailureToFailedState() {
        Request request = request();
        when(requestFactory.buildRequest("run-1")).thenReturn(request);
        when(client.runClustering(request)).thenThrow(new ClusteringClientException(
                ClusteringClientException.Kind.REMOTE_REJECTION,
                422,
                "CLUSTERING_COMPUTATION_FAILED",
                "remote failure",
                null
        ));

        worker.execute("run-1");

        verify(lifecycleService).markFailed(
                "run-1", "CLUSTERING_COMPUTATION_FAILED", "Python 聚类服务调用失败"
        );
        verify(lifecycleService, never()).complete("run-1", response());
    }

    private static Request request() {
        return new Request(
                "run-1", "version-1", ClusteringContracts.ALGORITHM, 2, 42,
                ClusteringContracts.FEATURE_SCHEMA_V2,
                List.of(sample("u1"), sample("u2"))
        );
    }

    private static FeatureSample sample(String userId) {
        return new FeatureSample(
                userId, List.of("AI"), "软件学院", "2024", List.of("周末"),
                1, 1, 0, 0, 0, null, Map.of("讲座", 1)
        );
    }

    private static Response response() {
        return new Response(
                "run-1", "version-1", "KMEANS", 2, 2,
                new Metrics(1.0, List.of(0.8, 0.2)),
                List.of(
                        new CommunitySummary(0, 1, List.of("AI")),
                        new CommunitySummary(1, 1, List.of("体育"))
                ),
                List.of(
                        new MemberResult("u1", 0, 10.0, 20.0, 0.5),
                        new MemberResult("u2", 1, 90.0, 80.0, 0.6)
                )
        );
    }
}
