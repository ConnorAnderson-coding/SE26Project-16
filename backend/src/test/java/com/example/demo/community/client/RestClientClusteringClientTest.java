package com.example.demo.community.client;

import com.example.demo.community.client.ClusteringClientException.Kind;
import com.example.demo.community.client.ClusteringContracts.FeatureSample;
import com.example.demo.community.client.ClusteringContracts.Request;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientClusteringClientTest {

    private MockWebServer server;
    private RestClientClusteringClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new RestClientClusteringClient(
                RestClient.builder().baseUrl(server.url("/").toString()).build(),
                new ObjectMapper()
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void decodesStrictSuccessfulResponse() throws InterruptedException {
        server.enqueue(json(200, """
                {
                  "runId":"run-1","version":"version-1","algorithm":"KMEANS",
                  "clusterCount":2,"sampleCount":2,
                  "metrics":{"inertia":1.5,"pcaExplainedVarianceRatio":[0.8,0.2]},
                  "communities":[
                    {"clusterNo":0,"memberCount":1,"topInterests":["AI"]},
                    {"clusterNo":1,"memberCount":1,"topInterests":["体育"]}
                  ],
                  "members":[
                    {"userId":"u1","clusterNo":0,"coordinateX":10.0,"coordinateY":20.0,"distanceToCenter":0.5},
                    {"userId":"u2","clusterNo":1,"coordinateX":90.0,"coordinateY":80.0,"distanceToCenter":0.6}
                  ]
                }
                """));

        var response = client.runClustering(request());

        assertThat(response.runId()).isEqualTo("run-1");
        assertThat(response.members()).hasSize(2);
        assertThat(server.takeRequest().getPath()).isEqualTo("/internal/v1/clustering/run");
    }

    @Test
    void mapsDocumentedRemoteRejectionWithoutLeakingDetails() {
        server.enqueue(json(422, """
                {"code":"CLUSTERING_COMPUTATION_FAILED","message":"无法计算","details":{"reason":"bad"}}
                """));

        assertThatThrownBy(() -> client.runClustering(request()))
                .isInstanceOfSatisfying(ClusteringClientException.class, exception -> {
                    assertThat(exception.getKind()).isEqualTo(Kind.REMOTE_REJECTION);
                    assertThat(exception.getStatusCode()).isEqualTo(422);
                    assertThat(exception.getRemoteCode()).isEqualTo("CLUSTERING_COMPUTATION_FAILED");
                });
    }

    @Test
    void rejectsUnknownFieldsInSuccessfulResponse() {
        server.enqueue(json(200, """
                {"runId":"run-1","version":"version-1","algorithm":"KMEANS","clusterCount":2,
                 "sampleCount":2,"metrics":{"inertia":1.0,"pcaExplainedVarianceRatio":[1.0,0.0]},
                 "communities":[],"members":[],"unexpected":"field"}
                """));

        assertThatThrownBy(() -> client.runClustering(request()))
                .isInstanceOfSatisfying(ClusteringClientException.class,
                        exception -> assertThat(exception.getKind()).isEqualTo(Kind.INVALID_RESPONSE));
    }

    @Test
    void rejectsStringToIntegerCoercion() {
        server.enqueue(json(200, """
                {"runId":"run-1","version":"version-1","algorithm":"KMEANS","clusterCount":"2",
                 "sampleCount":2,"metrics":{"inertia":1.0,"pcaExplainedVarianceRatio":[1.0,0.0]},
                 "communities":[],"members":[]}
                """));

        assertThatThrownBy(() -> client.runClustering(request()))
                .isInstanceOfSatisfying(ClusteringClientException.class,
                        exception -> assertThat(exception.getKind()).isEqualTo(Kind.INVALID_RESPONSE));
    }

    private Request request() {
        FeatureSample first = sample("u1", 1);
        FeatureSample second = sample("u2", 2);
        return new Request(
                "run-1", "version-1", "KMEANS", 2, 42,
                "community-features-v2", List.of(first, second)
        );
    }

    private FeatureSample sample(String userId, int signups) {
        return new FeatureSample(
                userId, List.of("AI"), "软件学院", "2024", List.of("周末"),
                signups, signups, 0, 0, 0, null, Map.of("讲座", signups)
        );
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(body);
    }
}
