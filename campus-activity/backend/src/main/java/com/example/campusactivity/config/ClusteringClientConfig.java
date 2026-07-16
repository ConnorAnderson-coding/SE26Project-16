package com.example.campusactivity.config;

import com.example.campusactivity.client.clustering.ClusteringClient;
import com.example.campusactivity.client.clustering.RestClientClusteringClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClusteringServiceProperties.class)
public class ClusteringClientConfig {
    @Bean
    @ConditionalOnProperty(
            prefix = "community-clustering.python",
            name = "enabled",
            havingValue = "true"
    )
    SimpleClientHttpRequestFactory clusteringClientRequestFactory(
            ClusteringServiceProperties properties
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return requestFactory;
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "community-clustering.python",
            name = "enabled",
            havingValue = "true"
    )
    ClusteringClient clusteringClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            ClusteringServiceProperties properties,
            SimpleClientHttpRequestFactory clusteringClientRequestFactory
    ) {
        String baseUrl = properties.baseUrl().toString();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        RestClient restClient = builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(clusteringClientRequestFactory)
                .build();
        return new RestClientClusteringClient(restClient, objectMapper);
    }
}
