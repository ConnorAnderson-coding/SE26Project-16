package com.example.demo.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import com.example.demo.common.BusinessException;
import com.example.demo.config.ElasticsearchProperties;
import com.example.demo.entity.Activity;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.search.config.ElasticsearchSearchInfrastructure;
import com.example.demo.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class ActivityIndexService {

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchProperties elasticsearchProperties;
    private final ActivityRepository activityRepository;

    public void indexActivity(Activity activity) {
        if (!ActivityDocumentMapper.isIndexable(activity)) {
            deleteActivity(activity.getId());
            return;
        }

        ActivityDocument document = ActivityDocumentMapper.toDocument(activity);
        try {
            IndexResponse response = elasticsearchClient.index(i -> i
                    .index(elasticsearchProperties.getIndexActivities())
                    .id(String.valueOf(document.id()))
                    .pipeline(ElasticsearchSearchInfrastructure.INGEST_PIPELINE)
                    .document(ActivityDocumentJson.toJsonData(document)));
            log.debug("Indexed activity {} -> result={}", document.id(), response.result());
        }
        catch (IOException ex) {
            throw new BusinessException("写入 Elasticsearch 索引失败: " + ex.getMessage());
        }
    }

    public void deleteActivity(Long activityId) {
        try {
            elasticsearchClient.delete(d -> d
                    .index(elasticsearchProperties.getIndexActivities())
                    .id(String.valueOf(activityId)));
        }
        catch (ElasticsearchException ex) {
            if (ex.status() == 404) {
                return;
            }
            throw new BusinessException("删除 Elasticsearch 索引文档失败: " + ex.getMessage());
        }
        catch (IOException ex) {
            throw new BusinessException("删除 Elasticsearch 索引文档失败: " + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public IndexRebuildResult rebuildAll() {
        requireAdmin();

        List<Activity> activities = activityRepository.findAllIndexable();
        String indexName = elasticsearchProperties.getIndexActivities();

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            for (Activity activity : activities) {
                ActivityDocument document = ActivityDocumentMapper.toDocument(activity);
                bulkBuilder.operations(op -> op.index(idx -> idx
                        .index(indexName)
                        .id(String.valueOf(document.id()))
                        .pipeline(ElasticsearchSearchInfrastructure.INGEST_PIPELINE)
                        .document(ActivityDocumentJson.toJsonData(document))));
            }

            if (activities.isEmpty()) {
                return new IndexRebuildResult(indexName, 0, 0, countIndexedDocuments());
            }

            BulkResponse bulkResponse = elasticsearchClient.bulk(
                    bulkBuilder.refresh(Refresh.WaitFor).build());
            long failed = bulkResponse.items().stream()
                    .filter(item -> item.error() != null)
                    .count();
            if (failed > 0) {
                bulkResponse.items().stream()
                        .filter(item -> item.error() != null)
                        .forEach(item -> log.error("Bulk index failed for id={}: {}", item.id(), item.error().reason()));
                throw new BusinessException("批量索引失败，失败条数: " + failed);
            }

            long indexedCount = countIndexedDocuments();
            return new IndexRebuildResult(indexName, activities.size(), failed, indexedCount);
        }
        catch (IOException ex) {
            throw new BusinessException("全量重建 Elasticsearch 索引失败: " + ex.getMessage());
        }
    }

    public IndexStats getStats() {
        requireAdmin();
        try {
            return new IndexStats(
                    elasticsearchProperties.getIndexActivities(),
                    countIndexedDocuments());
        }
        catch (IOException ex) {
            throw new BusinessException("查询 Elasticsearch 索引统计失败: " + ex.getMessage());
        }
    }

    private long countIndexedDocuments() throws IOException {
        return elasticsearchClient.count(new CountRequest.Builder()
                .index(elasticsearchProperties.getIndexActivities())
                .build()).count();
    }

    private void requireAdmin() {
        if (!"admin".equals(SecurityUtils.getCurrentUser().getUser().getRole())) {
            throw new BusinessException(403, "仅管理员可执行索引操作");
        }
    }

    public record IndexRebuildResult(
            String indexName,
            long sourceCount,
            long failedCount,
            long indexedCount) {
    }

    public record IndexStats(String indexName, long documentCount) {
    }
}
