package com.example.demo.search.support;

import com.example.demo.search.ActivitySearchHit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hybrid relevance: BM25 + dense semantic score with keyword-hit bonus.
 * Assumes semantic-only hits were already filtered by {@link SemanticScoreFilter}.
 */
public final class HybridRelevanceScorer {

    private HybridRelevanceScorer() {
    }

    /**
     * @param bm25Weight           weight on BM25 score normalized within this result set
     * @param semanticWeight       weight on ES semanticScore (≈ (1+cos)/2)
     * @param keywordBonus         flat bonus when the doc is a BM25 hit
     * @param semanticOnlyWeight   scale for semantic-only docs (no BM25 hit)
     */
    public static List<ActivitySearchHit> score(
            List<ActivitySearchHit> keywordHits,
            List<ActivitySearchHit> filteredSemanticHits,
            double bm25Weight,
            double semanticWeight,
            double keywordBonus,
            double semanticOnlyWeight) {
        Map<Long, Double> keywordScores = new HashMap<>();
        Map<Long, Double> semanticScores = new HashMap<>();
        Set<Long> ids = new LinkedHashSet<>();

        for (ActivitySearchHit hit : keywordHits) {
            ids.add(hit.activityId());
            if (hit.keywordScore() != null) {
                keywordScores.put(hit.activityId(), hit.keywordScore());
            }
        }
        for (ActivitySearchHit hit : filteredSemanticHits) {
            ids.add(hit.activityId());
            if (hit.semanticScore() != null) {
                semanticScores.put(hit.activityId(), hit.semanticScore());
            }
        }

        double maxBm25 = keywordScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

        List<ActivitySearchHit> ranked = new ArrayList<>();
        for (Long id : ids) {
            boolean keywordHit = keywordScores.containsKey(id);
            double bm25 = keywordScores.getOrDefault(id, 0.0);
            double bm25Norm = maxBm25 > 0.0 ? bm25 / maxBm25 : 0.0;
            double sem = semanticScores.getOrDefault(id, 0.0);

            double relevance;
            if (keywordHit) {
                relevance = bm25Weight * bm25Norm + semanticWeight * sem + keywordBonus;
            }
            else {
                relevance = semanticOnlyWeight * sem;
            }

            ranked.add(ActivitySearchHit.hybrid(
                    id,
                    keywordScores.get(id),
                    semanticScores.get(id),
                    relevance));
        }

        ranked.sort(Comparator.comparingDouble(ActivitySearchHit::fusedScore).reversed());
        return ranked;
    }
}
