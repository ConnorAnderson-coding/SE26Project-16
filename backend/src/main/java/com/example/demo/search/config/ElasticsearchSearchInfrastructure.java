package com.example.demo.search.config;

/**
 * Elasticsearch 语义检索基础设施常量。
 * GTE embedding ingest pipeline 由 {@code database/init-es.ps1} 创建。
 */
public final class ElasticsearchSearchInfrastructure {

    public static final String INGEST_PIPELINE = "campus-activity-embedding";

    private ElasticsearchSearchInfrastructure() {
    }
}
