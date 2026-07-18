package com.example.demo.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Configuration
public class ElasticsearchConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.search.elasticsearch")
    public ElasticsearchProperties elasticsearchProperties() {
        return new ElasticsearchProperties();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.search.elasticsearch", name = "enabled", havingValue = "true")
    public RestClient elasticsearchRestClient(ElasticsearchProperties properties) {
        HttpHost[] hosts = properties.getUris().stream()
                .map(HttpHost::create)
                .toArray(HttpHost[]::new);

        var builder = RestClient.builder(hosts);
        if (StringUtils.hasText(properties.getUsername())) {
            var credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(properties.getUsername(), properties.getPassword()));
            builder.setHttpClientConfigCallback(clientBuilder ->
                    clientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.search.elasticsearch", name = "enabled", havingValue = "true")
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.search.elasticsearch", name = "enabled", havingValue = "true")
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }

    public static class ElasticsearchProperties {
        private boolean enabled = false;
        private List<String> uris = List.of("http://localhost:9200");
        private String username;
        private String password;
        private String index = "campus_activities";
        private String textAnalyzer = "standard";
        private int vectorDims = 384;
        private int rrfRankConstant = 60;
        private double vectorMinScore = 0.68;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getUris() {
            return uris;
        }

        public void setUris(String uris) {
            this.uris = Arrays.stream(uris.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getIndex() {
            return index;
        }

        public void setIndex(String index) {
            this.index = index;
        }

        public String getTextAnalyzer() {
            return textAnalyzer;
        }

        public void setTextAnalyzer(String textAnalyzer) {
            this.textAnalyzer = textAnalyzer;
        }

        public int getVectorDims() {
            return vectorDims;
        }

        public void setVectorDims(int vectorDims) {
            this.vectorDims = vectorDims;
        }

        public int getRrfRankConstant() {
            return rrfRankConstant;
        }

        public void setRrfRankConstant(int rrfRankConstant) {
            this.rrfRankConstant = rrfRankConstant;
        }

        public double getVectorMinScore() {
            return vectorMinScore;
        }

        public void setVectorMinScore(double vectorMinScore) {
            this.vectorMinScore = vectorMinScore;
        }
    }
}
