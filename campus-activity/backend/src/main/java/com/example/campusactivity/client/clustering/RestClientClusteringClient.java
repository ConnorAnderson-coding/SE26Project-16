package com.example.campusactivity.client.clustering;

import com.example.campusactivity.client.clustering.dto.ClusteringErrorResponse;
import com.example.campusactivity.client.clustering.dto.ClusteringRequest;
import com.example.campusactivity.client.clustering.dto.ClusteringResponse;
import com.example.campusactivity.client.clustering.dto.HealthResponse;
import com.example.campusactivity.client.clustering.exception.ClusteringClientException;
import com.example.campusactivity.client.clustering.exception.ClusteringRemoteException;
import com.example.campusactivity.client.clustering.exception.ClusteringServiceUnavailableException;
import com.example.campusactivity.client.clustering.exception.InvalidClusteringServiceResponseException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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
        this.objectMapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }

    @Override
    public ClusteringResponse runClustering(ClusteringRequest request) {
        try {
            ResponseEntity<byte[]> response = restClient.post()
                    .uri(RUN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), this::handleErrorResponse)
                    .toEntity(byte[].class);
            return decodeSuccess(response, ClusteringResponse.class);
        } catch (ClusteringClientException exception) {
            throw exception;
        } catch (ResourceAccessException _exception) {
            throw new ClusteringServiceUnavailableException();
        } catch (RestClientException _exception) {
            throw new InvalidClusteringServiceResponseException();
        }
    }

    @Override
    public HealthResponse health() {
        try {
            ResponseEntity<byte[]> response = restClient.get()
                    .uri(HEALTH_PATH)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(status -> status.isError(), this::handleErrorResponse)
                    .toEntity(byte[].class);
            return decodeSuccess(response, HealthResponse.class);
        } catch (ClusteringClientException exception) {
            throw exception;
        } catch (ResourceAccessException _exception) {
            throw new ClusteringServiceUnavailableException();
        } catch (RestClientException _exception) {
            throw new InvalidClusteringServiceResponseException();
        }
    }

    private void handleErrorResponse(HttpRequest _request, ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        int statusCode = status.value();
        if (status.is5xxServerError()) {
            throw new ClusteringServiceUnavailableException(
                    statusCode,
                    decodeOptionalErrorCode(response)
            );
        }

        ClusteringErrorResponse error = decode(
                response.getHeaders(),
                response.getBody().readAllBytes(),
                ClusteringErrorResponse.class
        );
        throw new ClusteringRemoteException(
                statusCode,
                error.code(),
                error.message(),
                error.details()
        );
    }

    private String decodeOptionalErrorCode(ClientHttpResponse response) {
        try {
            ClusteringErrorResponse error = decode(
                    response.getHeaders(),
                    response.getBody().readAllBytes(),
                    ClusteringErrorResponse.class
            );
            return error.code();
        } catch (IOException | RuntimeException _exception) {
            return null;
        }
    }

    private <T> T decodeSuccess(ResponseEntity<byte[]> response, Class<T> responseType) {
        if (response.getStatusCode().value() != HttpStatus.OK.value()) {
            throw new InvalidClusteringServiceResponseException();
        }
        return decode(response.getHeaders(), response.getBody(), responseType);
    }

    private <T> T decode(HttpHeaders headers, byte[] body, Class<T> responseType) {
        validateJsonContentType(headers);
        if (body == null || body.length == 0) {
            throw new InvalidClusteringServiceResponseException();
        }
        try {
            return objectMapper.readValue(body, responseType);
        } catch (IOException | RuntimeException _exception) {
            throw new InvalidClusteringServiceResponseException();
        }
    }

    private void validateJsonContentType(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            throw new InvalidClusteringServiceResponseException();
        }
        if (contentType.getCharset() != null
                && !StandardCharsets.UTF_8.equals(contentType.getCharset())) {
            throw new InvalidClusteringServiceResponseException();
        }
    }
}
