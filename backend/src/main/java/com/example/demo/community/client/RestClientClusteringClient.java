package com.example.demo.community.client;

import com.example.demo.community.client.ClusteringClientException.Kind;
import com.example.demo.community.client.ClusteringContracts.ErrorResponse;
import com.example.demo.community.client.ClusteringContracts.HealthResponse;
import com.example.demo.community.client.ClusteringContracts.Request;
import com.example.demo.community.client.ClusteringContracts.Response;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class RestClientClusteringClient implements ClusteringClient {

    static final String RUN_PATH = "/internal/v1/clustering/run";
    static final String HEALTH_PATH = "/internal/v1/health";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RestClientClusteringClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature());
        this.objectMapper.coercionConfigFor(LogicalType.Integer)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        this.objectMapper.coercionConfigFor(LogicalType.Float)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }

    @Override
    public Response runClustering(Request request) {
        return exchange(
                restClient.post()
                        .uri(RUN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(request),
                Response.class
        );
    }

    @Override
    public HealthResponse health() {
        return exchange(
                restClient.get().uri(HEALTH_PATH).accept(MediaType.APPLICATION_JSON),
                HealthResponse.class
        );
    }

    private <T> T exchange(RestClient.RequestHeadersSpec<?> request, Class<T> responseType) {
        try {
            ResponseEntity<byte[]> response = request.retrieve()
                    .onStatus(status -> status.isError(), this::handleError)
                    .toEntity(byte[].class);
            if (response.getStatusCode().value() != 200) {
                throw invalidResponse(null);
            }
            return decode(response.getHeaders(), response.getBody(), responseType);
        } catch (ClusteringClientException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new ClusteringClientException(
                    Kind.UNAVAILABLE, null, null, "Python 聚类服务不可用", exception
            );
        } catch (RestClientException exception) {
            throw invalidResponse(exception);
        }
    }

    private void handleError(HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        byte[] body = response.getBody().readAllBytes();
        if (status >= 500) {
            String remoteCode = decodeOptionalCode(response.getHeaders(), body);
            throw new ClusteringClientException(
                    Kind.UNAVAILABLE, status, remoteCode, "Python 聚类服务暂不可用", null
            );
        }
        ErrorResponse error = decode(response.getHeaders(), body, ErrorResponse.class);
        throw new ClusteringClientException(
                Kind.REMOTE_REJECTION, status, error.code(), error.message(), null
        );
    }

    private String decodeOptionalCode(HttpHeaders headers, byte[] body) {
        try {
            return decode(headers, body, ErrorResponse.class).code();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private <T> T decode(HttpHeaders headers, byte[] body, Class<T> responseType) {
        MediaType contentType = headers.getContentType();
        if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                || (contentType.getCharset() != null
                && !StandardCharsets.UTF_8.equals(contentType.getCharset()))
                || body == null || body.length == 0) {
            throw invalidResponse(null);
        }
        try {
            return objectMapper.readValue(body, responseType);
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidResponse(exception);
        }
    }

    private static ClusteringClientException invalidResponse(Throwable cause) {
        return new ClusteringClientException(
                Kind.INVALID_RESPONSE, null, null, "Python 聚类服务响应不符合契约", cause
        );
    }
}
