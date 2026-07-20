package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.elasticsearch")
public class ElasticsearchProperties {

    private boolean enabled = true;
    /** Rebuild ES activity index from MySQL on startup when the index is empty. */
    private boolean autoRebuildOnStartup = true;
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
    /** Tuned on gte-small-zh cosine experiment (see report/cosine-threshold-experiment.md). */
    private double semanticAbsoluteThreshold = 0.90;
    /** Hybrid relevance: weight on BM25 score normalized within the recall set. */
    private double relevanceBm25Weight = 0.50;
    /** Hybrid relevance: weight on semanticScore for BM25-hit documents. */
    private double relevanceSemanticWeight = 0.35;
    /** Hybrid relevance: flat bonus when the document is a BM25 hit. */
    private double relevanceKeywordBonus = 0.15;
    /** Hybrid relevance: scale for semantic-only documents (no BM25 hit). */
    private double relevanceSemanticOnlyWeight = 0.90;

    /** Max recent registrations used for preference vector history. */
    private int prefHistorySize = 10;
    /** Half-life (days) for registration time decay: w = 0.5^(days/halfLife). */
    private double prefHalfLifeDays = 30.0;
    /** Convex mix weight on interest text vector (rest on history avg). */
    private double prefInterestMix = 0.4;
    private int recommendRecallSize = 50;
    /** Expanded kNN size when hard-filter leaves too few candidates. */
    private int recommendExpandRecallSize = 100;
    /** Re-expand / interest backfill if filtered candidates are below this. */
    private int recommendMinCandidates = 12;
    /**
     * If candidate sim max−min ≤ this, use absolute ES {@code _score}; otherwise min-max.
     */
    private double recommendSimSpanMin = 0.03;
    /**
     * Content-first: {@code final = sim + auxScale · aux} (non cold-start).
     */
    private double recommendAuxScale = 0.25;
    /** Cap for tag hits: {@code tagN = min(1, hits / tagCap)}. */
    private double recommendTagCap = 2.0;
    /** Cap for social: {@code socN = min(1, s / socialRef)}. */
    private double recommendSocialRef = 3.5;
    /** Kept for cold-start (folded into hot); non cold-start uses absolute sim + auxScale. */
    private double recommendWeightSim = 0.55;
    private double recommendWeightTag = 0.15;
    private double recommendWeightSocial = 0.05;
    private double recommendWeightHot = 0.10;
    private double recommendWeightTime = 0.05;
}
