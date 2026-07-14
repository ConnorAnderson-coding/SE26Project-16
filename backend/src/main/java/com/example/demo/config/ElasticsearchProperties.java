package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.elasticsearch")
public class ElasticsearchProperties {

    private boolean enabled = true;
    private String indexActivities = "campus_activities";
    /**
     * Dense text embedding model used for activity_embedding kNN.
     * Default: thenlper/gte-small-zh imported via Eland as {@code campus_gte} (512-d).
     */
    private String embeddingModelId = "campus_gte";
    private int searchRecallSize = 50;
    /** Kept for backward compatibility; hybrid fusion no longer uses RRF. */
    private int rrfRankConstant = 60;
    /**
     * Absolute cosine-derived score threshold for semantic-only hits.
     * ES cosine kNN scores are typically (1+cos)/2 in [0,1].
     */
    /** Tuned on gte-small-zh cosine experiment (see cosine-threshold-experiment.md). */
    private double semanticAbsoluteThreshold = 0.90;
    /** Hybrid relevance: weight on BM25 score normalized within the recall set. */
    private double relevanceBm25Weight = 0.50;
    /** Hybrid relevance: weight on semanticScore for BM25-hit documents. */
    private double relevanceSemanticWeight = 0.35;
    /** Hybrid relevance: flat bonus when the document is a BM25 hit. */
    private double relevanceKeywordBonus = 0.15;
    /** Hybrid relevance: scale for semantic-only documents (no BM25 hit). */
    private double relevanceSemanticOnlyWeight = 0.90;
}
