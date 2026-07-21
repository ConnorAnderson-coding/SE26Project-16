package com.example.campusactivity.client.clustering;

import com.example.campusactivity.client.clustering.dto.ClusteringRequest;
import com.example.campusactivity.client.clustering.dto.ClusteringResponse;
import com.example.campusactivity.client.clustering.dto.FeatureSample;
import com.example.campusactivity.client.clustering.dto.HealthResponse;
import com.example.campusactivity.client.clustering.exception.ClusteringClientException;
import com.example.campusactivity.client.clustering.exception.ClusteringRemoteException;
import com.example.campusactivity.client.clustering.exception.ClusteringServiceUnavailableException;
import com.example.campusactivity.client.clustering.exception.InvalidClusteringServiceResponseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestClientClusteringClientTest {
    private static final String BASE_URL = "http://clustering.test";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final String SUCCESS_BODY = """
            {
              "runId":"run-001",
              "version":"cc-test-001",
              "algorithm":"KMEANS",
              "clusterCount":2,
              "sampleCount":2,
              "metrics":{"inertia":0.0,"pcaExplainedVarianceRatio":[1.0,0.0]},
              "communities":[
                {"clusterNo":0,"memberCount":1,"topInterests":["AI","摄影"]},
                {"clusterNo":1,"memberCount":1,"topInterests":["羽毛球"]}
              ],
              "members":[
                {"userId":"20260001","clusterNo":0,"coordinateX":0.0,"coordinateY":50.0,"distanceToCenter":0.0},
                {"userId":"20260002","clusterNo":1,"coordinateX":100.0,"coordinateY":50.0,"distanceToCenter":0.0}
              ]
            }
            """;

    private MockRestServiceServer server;
    private ClusteringClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestClientClusteringClient(builder.build(), new ObjectMapper());
    }

    @Test
    void sendsExactRunRequestAndReadsSuccessResponse() {
        server.expect(once(), requestTo(BASE_URL + RestClientClusteringClient.RUN_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json("""
                        {
                          "runId":"run-001",
                          "version":"cc-test-001",
                          "algorithm":"KMEANS",
                          "clusterCount":2,
                          "randomState":42,
                          "featureSchemaVersion":"community-features-v1",
                          "samples":[
                            {
                              "userId":"20260001",
                              "interests":["AI","摄影"],
                              "college":"软件学院",
                              "grade":"2024级",
                              "availableTime":["weekday_evening","weekend"],
                              "signupCount":5,
                              "approvedSignupCount":4,
                              "favoriteCount":3,
                              "checkInCount":3,
                              "feedbackCount":2,
                              "averageRating":4.5,
                              "categoryParticipationCounts":{"academic":3,"sports":1}
                            },
                            {
                              "userId":"20260002",
                              "interests":["羽毛球"],
                              "college":null,
                              "grade":null,
                              "availableTime":[],
                              "signupCount":0,
                              "approvedSignupCount":0,
                              "favoriteCount":0,
                              "checkInCount":0,
                              "feedbackCount":0,
                              "averageRating":null,
                              "categoryParticipationCounts":{}
                            }
                          ]
                        }
                        """, true))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        ClusteringResponse response = client.runClustering(validRequest());

        assertThat(response.runId()).isEqualTo("run-001");
        assertThat(response.metrics().pcaExplainedVarianceRatio()).containsExactly(1.0, 0.0);
        assertThat(response.communities()).hasSize(2);
        assertThat(response.members())
                .extracting(member -> member.userId())
                .containsExactly("20260001", "20260002");
        server.verify();
    }

    @Test
    void readsHealthResponseWithoutSendingRequestBody() {
        server.expect(once(), requestTo(BASE_URL + RestClientClusteringClient.HEALTH_PATH))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("""
                        {
                          "status":"UP",
                          "service":"clustering-service",
                          "supportedFeatureSchemas":["community-features-v1"]
                        }
                        """, MediaType.APPLICATION_JSON));

        HealthResponse response = client.health();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.supportedFeatureSchemas()).containsExactly("community-features-v1");
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "400, INVALID_SAMPLE_DATA",
            "400, INVALID_CLUSTER_COUNT",
            "409, INVALID_FEATURE_SCHEMA",
            "409, UNSUPPORTED_FEATURE_SCHEMA",
            "422, CLUSTERING_COMPUTATION_FAILED",
            "422, CLUSTERING_COMPUTATION_ERROR"
    })
    void mapsFourHundredResponsesToBusinessErrors(int status, String code) {
        server.expect(once(), requestTo(BASE_URL + RestClientClusteringClient.RUN_PATH))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "code":"%s",
                                  "message":"聚类请求失败",
                                  "details":{"reason":"TEST_REASON"}
                                }
                                """.formatted(code)));

        assertThatThrownBy(() -> client.runClustering(validRequest()))
                .isInstanceOfSatisfying(ClusteringRemoteException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(status);
                    assertThat(exception.getErrorCode()).isEqualTo(code);
                    assertThat(exception.getMessage()).isEqualTo("Python 聚类服务返回业务错误");
                    assertThat(exception.getRemoteMessage()).isEqualTo("聚类请求失败");
                    assertThat(exception.getDetails()).isEmpty();
                    assertThat(exception.getCause()).isNull();
                });
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "500, INTERNAL_ERROR, INTERNAL_ERROR",
            "502, BAD_GATEWAY, UNKNOWN_REMOTE_ERROR",
            "503, SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE",
            "504, GATEWAY_TIMEOUT, UNKNOWN_REMOTE_ERROR"
    })
    void mapsFiveHundredResponsesToServiceUnavailable(
            int status,
            String remoteCode,
            String expectedCode
    ) {
        server.expect(once(), requestTo(BASE_URL + RestClientClusteringClient.RUN_PATH))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"%s","message":"聚类服务故障","details":{}}
                                """.formatted(remoteCode)));

        assertThatThrownBy(() -> client.runClustering(validRequest()))
                .isInstanceOfSatisfying(ClusteringServiceUnavailableException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(status);
                    assertThat(exception.getServiceErrorCode()).isEqualTo(expectedCode);
                    assertThat(exception.getCause()).isNull();
                });
        server.verify();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fiveHundredResponseBodies")
    void mapsEveryFiveHundredResponseToServiceUnavailable(
            String _caseName,
            int status,
            MediaType contentType,
            String responseBody,
            String expectedCode
    ) {
        server.expect(once(), requestTo(BASE_URL + RestClientClusteringClient.RUN_PATH))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .contentType(contentType)
                        .body(responseBody));

        Throwable exception = catchThrowable(() -> client.runClustering(validRequest()));

        assertThat(exception)
                .isInstanceOf(ClusteringServiceUnavailableException.class)
                .isNotInstanceOf(InvalidClusteringServiceResponseException.class)
                .hasMessage("Python 聚类服务当前不可用");
        ClusteringServiceUnavailableException unavailableException =
                (ClusteringServiceUnavailableException) exception;
        assertThat(unavailableException.getStatusCode()).isEqualTo(status);
        assertThat(unavailableException.getServiceErrorCode()).isEqualTo(expectedCode);
        assertThat(unavailableException.getCause()).isNull();
        if (!responseBody.isEmpty()) {
            assertSanitized(exception, responseBody);
        }
        server.verify();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unsafeRemoteErrorCodes")
    void replacesUnsafeFourHundredErrorCodes(
            String _caseName,
            String unsafeCode
    ) {
        server.expect(once(), requestTo(BASE_URL + RestClientClusteringClient.RUN_PATH))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorBody(unsafeCode)));

        Throwable exception = catchThrowable(() -> client.runClustering(validRequest()));

        assertThat(exception)
                .isInstanceOf(ClusteringRemoteException.class)
                .hasMessage("Python 聚类服务返回业务错误");
        ClusteringRemoteException remoteException = (ClusteringRemoteException) exception;
        assertThat(remoteException.getErrorCode())
                .isEqualTo(ClusteringClientException.UNKNOWN_REMOTE_ERROR)
                .doesNotContain(unsafeCode);
        assertThat(remoteException.getDetails()).isEmpty();
        assertSanitized(exception, unsafeCode);
        server.verify();
    }

    @Test
    void mapsTransportFailureWithoutExposingInternalAddress() {
        String featureValue = "PRIVATE_FEATURE_VALUE";
        server.expect(once(), requestTo(BASE_URL + RestClientClusteringClient.RUN_PATH))
                .andRespond(withException(new IOException(
                        "failed at " + BASE_URL + RestClientClusteringClient.RUN_PATH + " " + featureValue
                )));

        Throwable exception = catchThrowable(() -> client.runClustering(validRequest()));

        assertThat(exception)
                .isInstanceOf(ClusteringServiceUnavailableException.class)
                .hasMessage("Python 聚类服务当前不可用");
        assertSanitized(exception, BASE_URL, RestClientClusteringClient.RUN_PATH, featureValue);
        server.verify();
    }

    @Test
    void rejectsSuccessResponseWithUnknownField() {
        String responseSecret = "RAW_RESPONSE_SECRET";
        String featureValue = "PRIVATE_FEATURE_VALUE";
        String bodyWithUnknownField = SUCCESS_BODY.replace(
                "\"sampleCount\":2,",
                "\"sampleCount\":2,\"serviceUrl\":\"%s/%s/%s\",".formatted(
                        BASE_URL,
                        responseSecret,
                        featureValue
                )
        );
        server.expect(once(), requestTo(BASE_URL + RestClientClusteringClient.RUN_PATH))
                .andRespond(withSuccess(bodyWithUnknownField, MediaType.APPLICATION_JSON));

        Throwable exception = catchThrowable(() -> client.runClustering(validRequest()));

        assertThat(exception)
                .isInstanceOf(InvalidClusteringServiceResponseException.class)
                .hasMessage("Python 聚类服务返回了不符合契约的响应");
        assertSanitized(
                exception,
                BASE_URL,
                RestClientClusteringClient.RUN_PATH,
                responseSecret,
                featureValue,
                bodyWithUnknownField
        );
        server.verify();
    }

    @Test
    void rejectsScalarCoercionInSuccessResponse() {
        String bodyWithStringCount = SUCCESS_BODY.replace(
                "\"sampleCount\":2,",
                "\"sampleCount\":\"2\","
        );
        server.expect(once(), requestTo(BASE_URL + RestClientClusteringClient.RUN_PATH))
                .andRespond(withSuccess(bodyWithStringCount, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.runClustering(validRequest()))
                .isInstanceOf(InvalidClusteringServiceResponseException.class);
        server.verify();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSuccessResponses")
    void rejectsInvalidSuccessResponseAtClientBoundary(String _caseName, String responseBody) {
        server.expect(once(), requestTo(BASE_URL + RestClientClusteringClient.RUN_PATH))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        Throwable exception = catchThrowable(() -> client.runClustering(validRequest()));

        assertThat(exception)
                .isInstanceOf(InvalidClusteringServiceResponseException.class)
                .hasMessage("Python 聚类服务返回了不符合契约的响应");
        assertThat(exception.getCause()).isNull();
        server.verify();
    }

    @Test
    void acceptsDistinctMemberUserIds() {
        server.expect(once(), requestTo(BASE_URL + RestClientClusteringClient.RUN_PATH))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        ClusteringResponse response = client.runClustering(validRequest());

        assertThat(response.members())
                .extracting(member -> member.userId())
                .containsExactly("20260001", "20260002");
        server.verify();
    }

    @Test
    void rejectsExplicitNonUtf8JsonCharset() {
        MediaType nonUtf8Json = new MediaType(
                MediaType.APPLICATION_JSON,
                StandardCharsets.ISO_8859_1
        );
        server.expect(once(), requestTo(BASE_URL + RestClientClusteringClient.RUN_PATH))
                .andRespond(withSuccess(SUCCESS_BODY, nonUtf8Json));

        assertThatThrownBy(() -> client.runClustering(validRequest()))
                .isInstanceOf(InvalidClusteringServiceResponseException.class)
                .hasCause(null);
        server.verify();
    }

    @Test
    void rejectsMalformedUnifiedErrorBody() {
        server.expect(once(), requestTo(BASE_URL + RestClientClusteringClient.RUN_PATH))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"INVALID_SAMPLE_DATA\"}"));

        assertThatThrownBy(() -> client.runClustering(validRequest()))
                .isInstanceOf(InvalidClusteringServiceResponseException.class)
                .hasCause(null);
        server.verify();
    }

    @Test
    void rejectsSyntacticallyInvalidUnifiedErrorBody() {
        server.expect(once(), requestTo(BASE_URL + RestClientClusteringClient.RUN_PATH))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":"));

        assertThatThrownBy(() -> client.runClustering(validRequest()))
                .isInstanceOf(InvalidClusteringServiceResponseException.class)
                .hasCause(null);
        server.verify();
    }

    @Test
    void doesNotRetainRawFiveHundredResponseContent() {
        String rawResponseSecret = "RAW_HTTP_ERROR_BODY_SECRET";
        String featureValue = "PRIVATE_FEATURE_VALUE";
        server.expect(once(), requestTo(BASE_URL + RestClientClusteringClient.RUN_PATH))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "code":"INTERNAL_ERROR",
                                  "message":"%s %s%s",
                                  "details":{"unsafe":"%s"}
                                }
                                """.formatted(
                                rawResponseSecret,
                                BASE_URL,
                                RestClientClusteringClient.RUN_PATH,
                                featureValue
                        )));

        Throwable exception = catchThrowable(() -> client.runClustering(validRequest()));

        assertThat(exception)
                .isInstanceOf(ClusteringServiceUnavailableException.class)
                .hasMessage("Python 聚类服务当前不可用");
        assertSanitized(
                exception,
                rawResponseSecret,
                BASE_URL,
                RestClientClusteringClient.RUN_PATH,
                featureValue
        );
        server.verify();
    }

    @Test
    void rejectsNonJsonSuccessResponse() {
        server.expect(once(), requestTo(BASE_URL + RestClientClusteringClient.HEALTH_PATH))
                .andRespond(withSuccess("UP", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.health())
                .isInstanceOf(InvalidClusteringServiceResponseException.class);
        server.verify();
    }

    private static Stream<Arguments> fiveHundredResponseBodies() {
        return Stream.of(
                Arguments.of(
                        "空正文 500",
                        500,
                        MediaType.APPLICATION_JSON,
                        "",
                        ClusteringClientException.UNKNOWN_REMOTE_ERROR
                ),
                Arguments.of(
                        "非 JSON 500",
                        500,
                        MediaType.TEXT_PLAIN,
                        "upstream failure",
                        ClusteringClientException.UNKNOWN_REMOTE_ERROR
                ),
                Arguments.of(
                        "语法错误 JSON 500",
                        500,
                        MediaType.APPLICATION_JSON,
                        "{\"code\":",
                        ClusteringClientException.UNKNOWN_REMOTE_ERROR
                ),
                Arguments.of(
                        "缺少字段 JSON 500",
                        500,
                        MediaType.APPLICATION_JSON,
                        "{\"code\":\"INTERNAL_ERROR\"}",
                        ClusteringClientException.UNKNOWN_REMOTE_ERROR
                ),
                Arguments.of(
                        "合法统一错误体 500",
                        500,
                        MediaType.APPLICATION_JSON,
                        "{\"code\":\"INTERNAL_ERROR\",\"message\":\"故障\",\"details\":{}}",
                        "INTERNAL_ERROR"
                ),
                Arguments.of(
                        "空正文 502",
                        502,
                        MediaType.APPLICATION_JSON,
                        "",
                        ClusteringClientException.UNKNOWN_REMOTE_ERROR
                ),
                Arguments.of(
                        "非 JSON 503",
                        503,
                        MediaType.TEXT_PLAIN,
                        "service unavailable",
                        ClusteringClientException.UNKNOWN_REMOTE_ERROR
                ),
                Arguments.of(
                        "畸形 504",
                        504,
                        MediaType.APPLICATION_JSON,
                        "{not-json}",
                        ClusteringClientException.UNKNOWN_REMOTE_ERROR
                )
        );
    }

    private static Stream<Arguments> unsafeRemoteErrorCodes() {
        return Stream.of(
                Arguments.of("未知 code", "NOT_IN_CONTRACT"),
                Arguments.of("code 包含 baseUrl", BASE_URL),
                Arguments.of("code 包含请求路径", RestClientClusteringClient.RUN_PATH),
                Arguments.of("code 包含用户特征", "PRIVATE_FEATURE_VALUE"),
                Arguments.of("code 包含换行符", "UNSAFE\nCODE"),
                Arguments.of("code 超长", "X".repeat(512)),
                Arguments.of("code 为空白", "   ")
        );
    }

    private static String errorBody(String code) {
        try {
            return JSON_MAPPER.writeValueAsString(Map.of(
                    "code", code,
                    "message", "聚类请求失败",
                    "details", Map.of("unsafe", "PRIVATE_FEATURE_VALUE")
            ));
        } catch (IOException exception) {
            throw new IllegalStateException("无法构造测试响应", exception);
        }
    }

    private static Stream<Arguments> invalidSuccessResponses() {
        return Stream.of(
                Arguments.of("尾随 JSON 内容", SUCCESS_BODY + "{\"trailing\":true}"),
                Arguments.of(
                        "成功体 JSON 语法非法",
                        SUCCESS_BODY.replace("\"sampleCount\":2,", "\"sampleCount\":,")
                ),
                Arguments.of(
                        "缺少必填字段",
                        SUCCESS_BODY.replace("\"version\":\"cc-test-001\",", "")
                ),
                Arguments.of(
                        "数字字段拒绝字符串",
                        SUCCESS_BODY.replace("\"sampleCount\":2,", "\"sampleCount\":\"2\",")
                ),
                Arguments.of(
                        "inertia 拒绝负数",
                        SUCCESS_BODY.replace("\"inertia\":0.0", "\"inertia\":-0.1")
                ),
                Arguments.of(
                        "inertia 拒绝 NaN",
                        SUCCESS_BODY.replace("\"inertia\":0.0", "\"inertia\":NaN")
                ),
                Arguments.of(
                        "inertia 拒绝 Infinity",
                        SUCCESS_BODY.replace("\"inertia\":0.0", "\"inertia\":Infinity")
                ),
                Arguments.of(
                        "inertia 拒绝 -Infinity",
                        SUCCESS_BODY.replace("\"inertia\":0.0", "\"inertia\":-Infinity")
                ),
                Arguments.of(
                        "coordinateX 拒绝小于零",
                        SUCCESS_BODY.replace("\"coordinateX\":0.0", "\"coordinateX\":-0.1")
                ),
                Arguments.of(
                        "coordinateX 拒绝大于一百",
                        SUCCESS_BODY.replace("\"coordinateX\":0.0", "\"coordinateX\":100.1")
                ),
                Arguments.of(
                        "coordinateY 拒绝小于零",
                        SUCCESS_BODY.replace("\"coordinateY\":50.0", "\"coordinateY\":-0.1")
                ),
                Arguments.of(
                        "coordinateY 拒绝大于一百",
                        SUCCESS_BODY.replace("\"coordinateY\":50.0", "\"coordinateY\":100.1")
                ),
                Arguments.of(
                        "distanceToCenter 拒绝负数",
                        SUCCESS_BODY.replace(
                                "\"distanceToCenter\":0.0",
                                "\"distanceToCenter\":-0.1"
                        )
                ),
                Arguments.of(
                        "distanceToCenter 拒绝非有限数",
                        SUCCESS_BODY.replace(
                                "\"distanceToCenter\":0.0",
                                "\"distanceToCenter\":Infinity"
                        )
                ),
                Arguments.of(
                        "members 拒绝重复 userId",
                        SUCCESS_BODY.replace(
                                "\"userId\":\"20260002\"",
                                "\"userId\":\"20260001\""
                        )
                ),
                Arguments.of(
                        "member userId 拒绝空白值",
                        SUCCESS_BODY.replace(
                                "\"userId\":\"20260002\"",
                                "\"userId\":\"   \""
                        )
                )
        );
    }

    private static void assertSanitized(Throwable exception, String... forbiddenValues) {
        assertThat(exception.getCause()).isNull();
        StringWriter output = new StringWriter();
        exception.printStackTrace(new PrintWriter(output));
        assertThat(exception.getMessage()).doesNotContain(forbiddenValues);
        assertThat(exception.toString()).doesNotContain(forbiddenValues);
        assertThat(output.toString()).doesNotContain(forbiddenValues);
    }

    private static ClusteringRequest validRequest() {
        FeatureSample first = new FeatureSample(
                "20260001",
                List.of("AI", "摄影"),
                "软件学院",
                "2024级",
                List.of("weekday_evening", "weekend"),
                5,
                4,
                3,
                3,
                2,
                4.5,
                Map.of("academic", 3, "sports", 1)
        );
        FeatureSample second = new FeatureSample(
                "20260002",
                List.of("羽毛球"),
                null,
                null,
                List.of(),
                0,
                0,
                0,
                0,
                0,
                null,
                Map.of()
        );
        return new ClusteringRequest(
                "run-001",
                "cc-test-001",
                "KMEANS",
                2,
                42,
                "community-features-v1",
                List.of(first, second)
        );
    }
}
