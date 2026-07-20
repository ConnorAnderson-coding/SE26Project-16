package com.example.demo.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
        return rebuildAllInternal();
    }

    /**
     * Startup bootstrap: rebuild when ES has fewer docs than MySQL indexable activities.
     * Does not require admin authentication.
     */
    @Transactional(readOnly = true)
    public Optional<IndexRebuildResult> rebuildAllIfEmpty() {
        List<Activity> activities = activityRepository.findAllIndexable();
        if (activities.isEmpty()) {
            log.info("No indexable activities in MySQL, skip bootstrap rebuild");
            return Optional.empty();
        }

        long indexed;
        try {
            indexed = countIndexedDocuments();
        }
        catch (IOException ex) {
            log.warn("Cannot inspect Elasticsearch index, skip bootstrap rebuild: {}", ex.getMessage());
            return Optional.empty();
        }

        // Partial bulk failures leave a non-empty but incomplete index; still rebuild.
        if (indexed >= activities.size()) {
            log.debug("Elasticsearch index already populated ({}/{}), skip bootstrap rebuild",
                    indexed, activities.size());
            return Optional.empty();
        }

        log.info("Elasticsearch index incomplete ({}/{}); bootstrapping from MySQL",
                indexed, activities.size());
        return Optional.of(rebuildAllInternal());
    }

    private IndexRebuildResult rebuildAllInternal() {
        List<Activity> activities = activityRepository.findAllIndexable();
        String indexName = elasticsearchProperties.getIndexActivities();
        int batchSize = Math.max(1, elasticsearchProperties.getBulkBatchSize());
        long delayMs = Math.max(0L, elasticsearchProperties.getBulkBatchDelayMs());
        int maxRetries = Math.max(1, elasticsearchProperties.getBulkMaxRetries());

        if (activities.isEmpty()) {
            try {
                return new IndexRebuildResult(indexName, 0, 0, countIndexedDocuments());
            }
            catch (IOException ex) {
                throw new BusinessException("全量重建 Elasticsearch 索引失败: " + ex.getMessage());
            }
        }

        try {
            long failed = 0;
            for (int from = 0; from < activities.size(); from += batchSize) {
                int to = Math.min(from + batchSize, activities.size());
                List<Activity> batch = activities.subList(from, to);
                failed += indexBatchWithRetry(indexName, batch, maxRetries, delayMs);
                if (to < activities.size() && delayMs > 0) {
                    sleepQuietly(delayMs);
                }
                if (to == activities.size() || to % Math.max(batchSize * 5, 50) == 0 || to == batchSize) {
                    log.info("Indexed activities {}/{} (failed so far: {})", to, activities.size(), failed);
                }
            }

            // Final refresh so search sees all docs
            elasticsearchClient.indices().refresh(r -> r.index(indexName));

            if (failed > 0) {
                throw new BusinessException("批量索引失败，失败条数: " + failed);
            }

            long indexedCount = countIndexedDocuments();
            return new IndexRebuildResult(indexName, activities.size(), failed, indexedCount);
        }
        catch (IOException ex) {
            throw new BusinessException("全量重建 Elasticsearch 索引失败: " + ex.getMessage());
        }
    }

    private long indexBatchWithRetry(
            String indexName,
            List<Activity> batch,
            int maxRetries,
            long delayMs) throws IOException {
        List<Activity> pending = new ArrayList<>(batch);
        long hardFailures = 0;

        for (int attempt = 1; attempt <= maxRetries && !pending.isEmpty(); attempt++) {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            for (Activity activity : pending) {
                ActivityDocument document = ActivityDocumentMapper.toDocument(activity);
                bulkBuilder.operations(op -> op.index(idx -> idx
                        .index(indexName)
                        .id(String.valueOf(document.id()))
                        .pipeline(ElasticsearchSearchInfrastructure.INGEST_PIPELINE)
                        .document(ActivityDocumentJson.toJsonData(document))));
            }

            BulkResponse bulkResponse = elasticsearchClient.bulk(
                    bulkBuilder.refresh(Refresh.False).build());

            List<Activity> retryable = new ArrayList<>();
            for (int i = 0; i < bulkResponse.items().size(); i++) {
                BulkResponseItem item = bulkResponse.items().get(i);
                if (item.error() == null) {
                    continue;
                }
                String reason = item.error().reason() != null ? item.error().reason() : "";
                Activity activity = pending.get(i);
                if (isTransientInferenceError(reason) && attempt < maxRetries) {
                    retryable.add(activity);
                    log.debug("Retryable index failure id={} attempt={}/{}: {}",
                            item.id(), attempt, maxRetries, reason);
                }
                else {
                    hardFailures++;
                    log.error("Bulk index failed for id={}: {}", item.id(), reason);
                }
            }

            pending = retryable;
            if (!pending.isEmpty() && attempt < maxRetries) {
                // Back off harder when the ML inference queue is saturated
                sleepQuietly(delayMs * attempt + 1_000L);
            }
        }

        hardFailures += pending.size();
        for (Activity left : pending) {
            log.error("Bulk index exhausted retries for id={}", left.getId());
        }
        return hardFailures;
    }

    private static boolean isTransientInferenceError(String reason) {
        String lower = reason.toLowerCase(Locale.ROOT);
        return lower.contains("inference process queue is full")
                || lower.contains("queue_capacity")
                || lower.contains("timeout")
                || lower.contains("circuit_breaking_exception");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
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
