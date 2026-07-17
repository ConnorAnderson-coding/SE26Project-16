package com.example.demo.recommend;

import com.example.demo.config.ElasticsearchProperties;
import com.example.demo.entity.Activity;
import com.example.demo.recommend.support.ActivityTimeSlot;
import com.example.demo.recommend.support.VectorMath;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hard-filter + content-first ranking.
 * <p>
 * {@code final = simComponent + ε · aux} where {@code simComponent} is absolute ES
 * score when the candidate sim span is narrow (≤ δ), otherwise candidate min-max;
 * {@code aux} blends tag / social / hot / time with capped tag & social
 * (no fake-full min-max on constant features).
 */
@Component
public class RecommendationScorer {

    public static final String REASON_INTEREST = "兴趣匹配";
    public static final String REASON_CONTENT = "内容相似";
    public static final String REASON_SOCIAL = "社交相关";
    public static final String REASON_HOT = "热门活动";
    public static final String REASON_TIME = "时间合适";
    public static final String REASON_COLD = "热门推荐";

    public record ScoredActivity(
            Activity activity,
            double finalScore,
            double simScore,
            List<String> reasons
    ) {
    }

    public record SignedActivityWindow(long activityId, LocalDateTime start, LocalDateTime end) {
    }

    private record LabeledScore(String label, double score) {
    }

    private final ElasticsearchProperties properties;

    public RecommendationScorer(ElasticsearchProperties properties) {
        this.properties = properties;
    }

    public List<Activity> hardFilter(
            List<Activity> candidates,
            Set<Long> signedUpIds,
            List<SignedActivityWindow> signedWindows) {
        List<Activity> kept = new ArrayList<>();
        for (Activity a : candidates) {
            if (a.getId() == null || signedUpIds.contains(a.getId())) {
                continue;
            }
            if (overlapsAny(a.getStartTime(), a.getEndTime(), signedWindows)) {
                continue;
            }
            kept.add(a);
        }
        return kept;
    }

    public List<ScoredActivity> scoreAndRank(
            List<Activity> filtered,
            Map<Long, Double> simById,
            List<String> interests,
            List<String> availableTime,
            Map<String, Double> socialByOrganizer,
            boolean coldStart) {
        if (filtered.isEmpty()) {
            return List.of();
        }

        int n = filtered.size();
        double[] sims = new double[n];
        double[] tags = new double[n];
        double[] socials = new double[n];
        double[] hots = new double[n];
        double[] times = new double[n];

        for (int i = 0; i < n; i++) {
            Activity a = filtered.get(i);
            sims[i] = simById.getOrDefault(a.getId(), 0.0);
            tags[i] = tagMatchCount(a, interests);
            socials[i] = socialByOrganizer.getOrDefault(a.getOrganizerId(), 0.0);
            if (socials[i] == 0.0 && a.getOrganizer() != null) {
                socials[i] = socialByOrganizer.getOrDefault(a.getOrganizer().getId(), 0.0);
            }
            int signup = a.getSignupCount() != null ? a.getSignupCount() : 0;
            int favorite = a.getFavoriteCount() != null ? a.getFavoriteCount() : 0;
            hots[i] = Math.log1p(signup + favorite);
            times[i] = ActivityTimeSlot.timeFit(a.getStartTime(), availableTime);
        }

        double[] simComponent = resolveSimComponent(sims, coldStart);
        double tagCap = Math.max(1.0, properties.getRecommendTagCap());
        double socialRef = Math.max(1e-6, properties.getRecommendSocialRef());
        double[] tagN = new double[n];
        double[] socN = new double[n];
        for (int i = 0; i < n; i++) {
            tagN[i] = Math.min(1.0, tags[i] / tagCap);
            socN[i] = Math.min(1.0, socials[i] / socialRef);
        }
        double[] hotN = normalizeArray(hots);

        double wTag = properties.getRecommendWeightTag();
        double wSoc = properties.getRecommendWeightSocial();
        double wHot = properties.getRecommendWeightHot();
        double wTime = properties.getRecommendWeightTime();
        if (coldStart) {
            wHot += properties.getRecommendWeightSim();
        }
        double wAux = wTag + wSoc + wHot + wTime;
        if (wAux <= 0) {
            wAux = 1.0;
        }

        double eps = Math.max(0.0, properties.getRecommendAuxScale());
        double simMedian = median(sims);
        boolean hasTimePrefs = availableTime != null && !availableTime.isEmpty();

        List<ScoredActivity> scored = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double cTag = (wTag * tagN[i]) / wAux;
            double cSoc = (wSoc * socN[i]) / wAux;
            double cHot = (wHot * hotN[i]) / wAux;
            double cTime = (wTime * times[i]) / wAux;
            double aux = cTag + cSoc + cHot + cTime;
            double finalScore = coldStart
                    ? aux
                    : simComponent[i] + eps * aux;

            List<String> reasons = buildReasons(
                    coldStart,
                    sims[i],
                    simMedian,
                    simComponent[i],
                    tagN[i],
                    socN[i],
                    hotN[i],
                    times[i],
                    hasTimePrefs,
                    coldStart ? cTag : eps * cTag,
                    coldStart ? cSoc : eps * cSoc,
                    coldStart ? cHot : eps * cHot,
                    coldStart ? cTime : eps * cTime,
                    coldStart ? 0.0 : simComponent[i]
            );
            scored.add(new ScoredActivity(filtered.get(i), finalScore, sims[i], reasons));
        }
        scored.sort(Comparator.comparingDouble(ScoredActivity::finalScore).reversed());
        return scored;
    }

    /**
     * Pick up to 3 human-readable reasons ordered by contribution.
     * Low-sim items that ride on social/hot still surface those labels.
     */
    static List<String> buildReasons(
            boolean coldStart,
            double simRaw,
            double simMedian,
            double simComp,
            double tagN,
            double socN,
            double hotN,
            double timeFit,
            boolean hasTimePrefs,
            double contribTag,
            double contribSoc,
            double contribHot,
            double contribTime,
            double contribSim) {
        List<LabeledScore> candidates = new ArrayList<>();

        if (tagN >= 0.5) {
            candidates.add(new LabeledScore(REASON_INTEREST, contribTag + 0.02));
        }
        if (!coldStart && (simRaw >= simMedian || simComp >= 0.92)) {
            candidates.add(new LabeledScore(REASON_CONTENT, contribSim));
        }
        if (socN >= 0.25) {
            // Boost social label when content is weak relative to the pool
            double boost = (!coldStart && simRaw < simMedian) ? 0.05 : 0.0;
            candidates.add(new LabeledScore(REASON_SOCIAL, contribSoc + boost));
        }
        if (hotN >= 0.55) {
            candidates.add(new LabeledScore(REASON_HOT, contribHot));
        }
        if (hasTimePrefs && timeFit >= 1.0) {
            candidates.add(new LabeledScore(REASON_TIME, contribTime));
        }
        if (coldStart && candidates.isEmpty()) {
            return List.of(REASON_COLD);
        }
        if (candidates.isEmpty()) {
            if (hotN > 0) {
                return List.of(REASON_HOT);
            }
            return coldStart ? List.of(REASON_COLD) : List.of(REASON_CONTENT);
        }

        candidates.sort(Comparator.comparingDouble(LabeledScore::score).reversed());
        List<String> out = new ArrayList<>();
        for (LabeledScore ls : candidates) {
            if (out.size() >= 3) {
                break;
            }
            if (!out.contains(ls.label())) {
                out.add(ls.label());
            }
        }
        return List.copyOf(out);
    }

    /**
     * Narrow sim pools keep absolute ES score; wide pools use min-max so relative
     * gaps remain visible. Degenerate min-max → 0 (see {@link VectorMath#minMaxNormalize}).
     */
    double[] resolveSimComponent(double[] sims, boolean coldStart) {
        double[] out = new double[sims.length];
        if (coldStart) {
            return out;
        }
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double v : sims) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double span = max - min;
        double delta = properties.getRecommendSimSpanMin();
        if (span > delta) {
            return normalizeArray(sims);
        }
        System.arraycopy(sims, 0, out, 0, sims.length);
        return out;
    }

    static boolean overlapsAny(
            LocalDateTime start,
            LocalDateTime end,
            List<SignedActivityWindow> windows) {
        if (start == null || end == null || windows == null) {
            return false;
        }
        for (SignedActivityWindow w : windows) {
            if (w.start() == null || w.end() == null) {
                continue;
            }
            if (start.isBefore(w.end()) && end.isAfter(w.start())) {
                return true;
            }
        }
        return false;
    }

    static int tagMatchCount(Activity activity, List<String> interests) {
        if (interests == null || interests.isEmpty()) {
            return 0;
        }
        Set<String> want = new HashSet<>();
        for (String i : interests) {
            if (i != null && !i.isBlank()) {
                want.add(i.trim());
            }
        }
        int hits = 0;
        if (activity.getCategory() != null && want.contains(activity.getCategory())) {
            hits++;
        }
        if (activity.getTags() != null) {
            for (String tag : activity.getTags()) {
                if (tag != null && want.contains(tag)) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private static double[] normalizeArray(double[] values) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double v : values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double[] out = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = VectorMath.minMaxNormalize(values[i], min, max);
        }
        return out;
    }

    private static double median(double[] values) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        double[] copy = values.clone();
        java.util.Arrays.sort(copy);
        int mid = copy.length / 2;
        if (copy.length % 2 == 0) {
            return (copy[mid - 1] + copy[mid]) / 2.0;
        }
        return copy[mid];
    }
}
