package com.example.demo.search.support;

import com.example.demo.search.ActivitySearchHit;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SemanticScoreFilter {

    private SemanticScoreFilter() {
    }

    /**
     * 保留 BM25 命中的文档；仅语义召回的文档需满足绝对阈值（cosine 映射分）。
     */
    public static List<ActivitySearchHit> filterSemanticOnly(
            List<ActivitySearchHit> keywordHits,
            List<ActivitySearchHit> semanticHits,
            double absoluteThreshold) {
        if (semanticHits.isEmpty()) {
            return semanticHits;
        }

        Set<Long> keywordIds = new HashSet<>();
        for (ActivitySearchHit hit : keywordHits) {
            keywordIds.add(hit.activityId());
        }

        return semanticHits.stream()
                .filter(hit -> {
                    if (keywordIds.contains(hit.activityId())) {
                        return true;
                    }
                    Double semanticScore = hit.semanticScore();
                    return semanticScore != null && semanticScore >= absoluteThreshold;
                })
                .toList();
    }
}
