package com.example.demo.config;

import com.example.demo.community.client.ClusteringClient;
import com.example.demo.community.client.RestClientClusteringClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(ClusteringServiceProperties.class)
public class ClusteringClientConfig {

    @Bean
    @ConditionalOnProperty(prefix = "community-clustering.python", name = "enabled", havingValue = "true")
    ClusteringClient clusteringClient(
            ClusteringServiceProperties properties,
            ObjectMapper objectMapper
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
        return new RestClientClusteringClient(restClient, objectMapper);
    }
}
