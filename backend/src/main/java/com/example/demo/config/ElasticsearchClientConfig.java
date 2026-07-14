package com.example.demo.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * 显式创建 Elasticsearch Java API Client，避免与 Spring Boot 自动配置重复注册 Bean，
 * 并确保使用 ES 8 兼容的 Content-Type / Accept 协商（勿手动覆盖这些头）。
 */
@Configuration
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class ElasticsearchClientConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public RestClient elasticsearchRestClient(
            @Value("${spring.elasticsearch.uris:http://localhost:9200}") String uris) {
        URI uri = URI.create(uris.split(",")[0].trim());
        int port = uri.getPort() > 0 ? uri.getPort() : 9200;
        String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
        return RestClient.builder(new HttpHost(uri.getHost(), port, scheme)).build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new RestClientTransport(restClient, new JacksonJsonpMapper(mapper));
    }

    @Bean
    @ConditionalOnMissingBean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
