package com.example.campusactivity.config;

import com.example.campusactivity.client.clustering.ClusteringClient;
import com.example.campusactivity.client.clustering.RestClientClusteringClient;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ClusteringClientConfigTest {
    private static final String ENABLED = "community-clustering.python.enabled=true";
    private static final String VALID_BASE_URL =
            "community-clustering.python.base-url=http://localhost:8000";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestDependencies.class, ClusteringClientConfig.class);

    @Test
    void startsWithoutClientWhenDisabled() {
        contextRunner
                .withPropertyValues("community-clustering.python.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ClusteringClient.class);
                    assertThat(context).doesNotHaveBean(SimpleClientHttpRequestFactory.class);
                });
    }

    @Test
    void createsClientWhenEnabledWithValidBaseUrl() {
        contextRunner
                .withPropertyValues(ENABLED, VALID_BASE_URL)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ClusteringClient.class);
                    assertThat(context).hasSingleBean(SimpleClientHttpRequestFactory.class);
                });
    }

    @Test
    void failsWhenEnabledWithoutBaseUrl() {
        contextRunner
                .withPropertyValues(ENABLED)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenEnabledWithBlankBaseUrl() {
        contextRunner
                .withPropertyValues(ENABLED, "community-clustering.python.base-url=")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://user:password@localhost:8000",
            "http://localhost:8000?mode=test",
            "http://localhost:8000#fragment",
            "http://localhost:8000/internal",
            "ftp://localhost:8000",
            "http:///",
            "http://localhost:0",
            "http://localhost:65536",
            "http://localhost:-1",
            "http://localhost:not-a-port"
    })
    void rejectsInvalidBaseUrls(String baseUrl) {
        contextRunner
                .withPropertyValues(
                        ENABLED,
                        "community-clustering.python.base-url=" + baseUrl
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost",
            "https://localhost",
            "http://localhost:1",
            "http://localhost:65535"
    })
    void acceptsDefaultAndBoundaryPorts(String baseUrl) {
        contextRunner
                .withPropertyValues(
                        ENABLED,
                        "community-clustering.python.base-url=" + baseUrl
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ClusteringClient.class);
                });
    }

    @ParameterizedTest
    @CsvSource({
            "connect-timeout, 0s",
            "connect-timeout, -1ms",
            "connect-timeout, PT0.000000001S",
            "read-timeout, 0s",
            "read-timeout, -1ms",
            "read-timeout, PT0.000000001S"
    })
    void rejectsNonPositiveTimeouts(String propertyName, String value) {
        contextRunner
                .withPropertyValues(
                        ENABLED,
                        VALID_BASE_URL,
                        "community-clustering.python." + propertyName + "=" + value
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @CsvSource({
            "connect-timeout, connectTimeout",
            "read-timeout, readTimeout"
    })
    void acceptsOneMillisecondTimeout(
            String propertyName,
            String requestFactoryField
    ) {
        contextRunner
                .withPropertyValues(
                        ENABLED,
                        VALID_BASE_URL,
                        "community-clustering.python." + propertyName + "=1ms"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SimpleClientHttpRequestFactory requestFactory = context.getBean(
                            SimpleClientHttpRequestFactory.class
                    );
                    assertThat(ReflectionTestUtils.getField(requestFactory, requestFactoryField))
                            .isEqualTo(1);
                });
    }

    @ParameterizedTest
    @CsvSource({
            "connect-timeout, connectTimeout",
            "read-timeout, readTimeout"
    })
    void acceptsMaximumClientTimeout(
            String propertyName,
            String requestFactoryField
    ) {
        contextRunner
                .withPropertyValues(
                        ENABLED,
                        VALID_BASE_URL,
                        "community-clustering.python." + propertyName + "=2147483647ms"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ClusteringServiceProperties properties = context.getBean(
                            ClusteringServiceProperties.class
                    );
                    assertThat(ClusteringServiceProperties.MAX_CLIENT_TIMEOUT)
                            .isEqualTo(Duration.ofMillis(Integer.MAX_VALUE));
                    Duration configuredTimeout = "connect-timeout".equals(propertyName)
                            ? properties.connectTimeout()
                            : properties.readTimeout();
                    assertThat(configuredTimeout)
                            .isEqualTo(ClusteringServiceProperties.MAX_CLIENT_TIMEOUT);

                    SimpleClientHttpRequestFactory requestFactory = context.getBean(
                            SimpleClientHttpRequestFactory.class
                    );
                    assertThat(ReflectionTestUtils.getField(requestFactory, requestFactoryField))
                            .isEqualTo(Integer.MAX_VALUE);
                });
    }

    @ParameterizedTest
    @CsvSource({
            "connect-timeout, 2147483648ms",
            "read-timeout, 2147483648ms",
            "connect-timeout, 9223372036854775807ms",
            "read-timeout, 9223372036854775807ms"
    })
    void rejectsTimeoutsAboveSafeIntegerMillisecondRange(
            String propertyName,
            String value
    ) {
        contextRunner
                .withPropertyValues(
                        ENABLED,
                        VALID_BASE_URL,
                        "community-clustering.python." + propertyName + "=" + value
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void bindsDurationsAndAppliesThemToActualRequestFactory() {
        contextRunner
                .withPropertyValues(
                        ENABLED,
                        VALID_BASE_URL,
                        "community-clustering.python.connect-timeout=1500ms",
                        "community-clustering.python.read-timeout=45s"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ClusteringServiceProperties properties = context.getBean(
                            ClusteringServiceProperties.class
                    );
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(1500));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(45));

                    SimpleClientHttpRequestFactory requestFactory = context.getBean(
                            SimpleClientHttpRequestFactory.class
                    );
                    assertThat(ReflectionTestUtils.getField(requestFactory, "connectTimeout"))
                            .isEqualTo(1500);
                    assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout"))
                            .isEqualTo(45_000);

                    RestClientClusteringClient client = (RestClientClusteringClient) context.getBean(
                            ClusteringClient.class
                    );
                    RestClient restClient = (RestClient) ReflectionTestUtils.getField(
                            client,
                            "restClient"
                    );
                    assertThat(restClient).isNotNull();
                    assertThat(ReflectionTestUtils.getField(restClient, "clientRequestFactory"))
                            .isSameAs(requestFactory);
                });
    }

    @Test
    void usesStrictObjectMapperCopyWithoutChangingGlobalMapper() {
        contextRunner
                .withPropertyValues(ENABLED, VALID_BASE_URL)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ObjectMapper globalMapper = context.getBean(ObjectMapper.class);
                    RestClientClusteringClient client = (RestClientClusteringClient) context.getBean(
                            ClusteringClient.class
                    );
                    ObjectMapper clientMapper = (ObjectMapper) ReflectionTestUtils.getField(
                            client,
                            "objectMapper"
                    );

                    assertThat(clientMapper).isNotNull().isNotSameAs(globalMapper);
                    assertThat(clientMapper.isEnabled(
                            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
                    )).isTrue();
                    assertThat(clientMapper.isEnabled(
                            DeserializationFeature.FAIL_ON_TRAILING_TOKENS
                    )).isTrue();
                    assertThat(clientMapper.getFactory().isEnabled(
                            JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature()
                    )).isFalse();

                    assertThat(globalMapper.isEnabled(
                            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
                    )).isFalse();
                    assertThat(globalMapper.isEnabled(
                            DeserializationFeature.FAIL_ON_TRAILING_TOKENS
                    )).isFalse();
                    assertThat(globalMapper.getFactory().isEnabled(
                            JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature()
                    )).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestDependencies {
        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        ObjectMapper objectMapper() {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            objectMapper.disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
            objectMapper.enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature());
            return objectMapper;
        }
    }
}
