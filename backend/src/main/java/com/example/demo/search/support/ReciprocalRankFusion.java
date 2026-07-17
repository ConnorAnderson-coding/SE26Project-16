package com.example.demo.search.support;

import com.example.demo.search.ActivitySearchHit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReciprocalRankFusion {

    private ReciprocalRankFusion() {
    }

    /**
     * RRF score = sum(1 / (k + rank_i)) across retrieval channels.
     */
    public static List<ActivitySearchHit> fuse(List<List<ActivitySearchHit>> rankedLists, int rankConstant) {
        Map<Long, Double> fusedScores = new LinkedHashMap<>();
        Map<Long, Double> keywordScores = new HashMap<>();
        Map<Long, Double> semanticScores = new HashMap<>();

        for (List<ActivitySearchHit> list : rankedLists) {
            for (int rank = 0; rank < list.size(); rank++) {
                ActivitySearchHit hit = list.get(rank);
                double contribution = 1.0 / (rankConstant + rank + 1);
                fusedScores.merge(hit.activityId(), contribution, Double::sum);
                if (hit.keywordScore() != null) {
                    keywordScores.put(hit.activityId(), hit.keywordScore());
                }
                if (hit.semanticScore() != null) {
                    semanticScores.put(hit.activityId(), hit.semanticScore());
                }
            }
        }

        List<Map.Entry<Long, Double>> sorted = new ArrayList<>(fusedScores.entrySet());
        sorted.sort(Comparator.comparingDouble(Map.Entry<Long, Double>::getValue).reversed());

        return sorted.stream()
                .map(entry -> ActivitySearchHit.hybrid(
                        entry.getKey(),
                        keywordScores.get(entry.getKey()),
                        semanticScores.get(entry.getKey()),
                        entry.getValue()))
                .toList();
    }
}
