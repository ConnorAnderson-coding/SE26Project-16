package com.example.demo.search.config;

import com.example.demo.config.ElasticsearchProperties;
import com.example.demo.search.ActivityIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * On application startup, rebuild the activities search index from MySQL when ES is empty.
 * Requires {@code database/init-es.ps1} to have created the index and deployed {@code campus_gte}.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class ActivitySearchIndexBootstrap implements ApplicationRunner {

    private static final int MAX_ATTEMPTS = 6;
    private static final long RETRY_DELAY_MS = 5_000L;

    private final ElasticsearchProperties elasticsearchProperties;
    private final ActivityIndexService activityIndexService;

    @Override
    public void run(ApplicationArguments args) {
        if (!elasticsearchProperties.isAutoRebuildOnStartup()) {
            log.info("Elasticsearch auto-rebuild on startup is disabled");
            return;
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                activityIndexService.rebuildAllIfEmpty().ifPresent(result ->
                        log.info("Elasticsearch bootstrap rebuild complete: index={}, source={}, indexed={}",
                                result.indexName(), result.sourceCount(), result.indexedCount()));
                return;
            }
            catch (RuntimeException ex) {
                if (attempt >= MAX_ATTEMPTS) {
                    log.warn(
                            "Elasticsearch bootstrap rebuild failed after {} attempts: {}. "
                                    + "Ensure database/init-es.ps1 has been run, then restart backend or call "
                                    + "POST /api/v1/search/index/rebuild",
                            MAX_ATTEMPTS,
                            ex.getMessage());
                    return;
                }
                log.warn("Elasticsearch bootstrap rebuild attempt {}/{} failed: {}",
                        attempt, MAX_ATTEMPTS, ex.getMessage());
                sleepQuietly(RETRY_DELAY_MS);
            }
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
