package com.example.demo.recommend;

import com.example.demo.config.ElasticsearchProperties;
import com.example.demo.dto.DtoMapper;
import com.example.demo.dto.response.ActivityResponse;
import com.example.demo.entity.Activity;
import com.example.demo.entity.Registration;
import com.example.demo.entity.User;
import com.example.demo.recommend.repository.ElasticsearchRecommendationRepository;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.service.UserService;
import com.example.demo.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class RecommendationService {

    private final UserService userService;
    private final RegistrationRepository registrationRepository;
    private final ActivityRepository activityRepository;
    private final UserPreferenceVectorService userPreferenceVectorService;
    private final ElasticsearchRecommendationRepository elasticsearchRecommendationRepository;
    private final SocialAffinityService socialAffinityService;
    private final RecommendationScorer recommendationScorer;
    private final ElasticsearchProperties elasticsearchProperties;

    @Transactional(readOnly = true)
    public List<ActivityResponse> recommend(int limit) {
        int topN = Math.max(1, limit);
        String userId = SecurityUtils.getCurrentUserId();
        User user = userService.getUserEntity(userId);

        List<Registration> regs = registrationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(r -> r.getStatus() == null || !"rejected".equals(r.getStatus()))
                .toList();
        Set<Long> signedIds = new HashSet<>();
        List<RecommendationScorer.SignedActivityWindow> windows = new ArrayList<>();
        for (Registration r : regs) {
            Activity a = r.getActivity();
            if (a == null || a.getId() == null) {
                continue;
            }
            signedIds.add(a.getId());
            windows.add(new RecommendationScorer.SignedActivityWindow(
                    a.getId(), a.getStartTime(), a.getEndTime()));
        }

        float[] pref = null;
        try {
            pref = userPreferenceVectorService.getOrBuild(user);
        }
        catch (RuntimeException ex) {
            log.warn("Preference vector build failed, cold-start path: {}", ex.getMessage());
        }

        boolean coldStart = pref == null;
        List<RecommendationHit> hits;
        if (coldStart) {
            hits = coldStartCandidates(elasticsearchProperties.getRecommendRecallSize());
        }
        else {
            try {
                hits = elasticsearchRecommendationRepository.knnByVector(
                        pref, elasticsearchProperties.getRecommendRecallSize());
            }
            catch (RuntimeException ex) {
                log.warn("Recommend knn failed, cold-start path: {}", ex.getMessage());
                hits = coldStartCandidates(elasticsearchProperties.getRecommendRecallSize());
                coldStart = true;
            }
        }

        Map<Long, Double> simById = new LinkedHashMap<>();
        for (RecommendationHit hit : hits) {
            simById.putIfAbsent(hit.activityId(), hit.simScore());
        }

        List<String> interests = user.getInterests() != null ? user.getInterests() : List.of();
        List<Activity> filtered = loadFilterCandidates(simById, signedIds, windows);

        if (!coldStart && pref != null
                && filtered.size() < elasticsearchProperties.getRecommendMinCandidates()) {
            expandRecall(pref, simById, interests, signedIds, windows);
            filtered = loadFilterCandidates(simById, signedIds, windows);
        }

        if (filtered.isEmpty()) {
            return List.of();
        }

        List<String> organizerIds = filtered.stream()
                .map(a -> a.getOrganizerId() != null
                        ? a.getOrganizerId()
                        : (a.getOrganizer() != null ? a.getOrganizer().getId() : null))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, Double> social = socialAffinityService.scoresForOrganizers(userId, organizerIds);

        List<String> available = user.getAvailableTime() != null ? user.getAvailableTime() : List.of();

        List<RecommendationScorer.ScoredActivity> ranked = recommendationScorer.scoreAndRank(
                filtered, simById, interests, available, social, coldStart);

        double denom = coldStart
                ? 1.0
                : 1.0 + Math.max(0.0, elasticsearchProperties.getRecommendAuxScale());

        List<ActivityResponse> result = new ArrayList<>();
        for (RecommendationScorer.ScoredActivity s : ranked) {
            if (result.size() >= topN) {
                break;
            }
            ActivityResponse response = DtoMapper.toActivityResponse(s.activity());
            double display = Math.max(0.0, Math.min(1.0, s.finalScore() / denom));
            response.setRecommendScore((int) Math.round(display * 100.0));
            response.setRecommendReasons(s.reasons());
            result.add(response);
        }
        return result;
    }

    private List<Activity> loadFilterCandidates(
            Map<Long, Double> simById,
            Set<Long> signedIds,
            List<RecommendationScorer.SignedActivityWindow> windows) {
        if (simById.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>(simById.keySet());
        Map<Long, Activity> activityMap = activityRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Activity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<Activity> ordered = new ArrayList<>();
        for (Long id : ids) {
            Activity a = activityMap.get(id);
            if (a != null && "published".equals(a.getStatus())) {
                ordered.add(a);
            }
        }
        return recommendationScorer.hardFilter(ordered, signedIds, windows);
    }

    /**
     * When hard-filter empties the pool: enlarge kNN, then backfill interest-tagged
     * published activities with a modest sim prior so scoring can still run content-first.
     */
    private void expandRecall(
            float[] pref,
            Map<Long, Double> simById,
            List<String> interests,
            Set<Long> signedIds,
            List<RecommendationScorer.SignedActivityWindow> windows) {
        int expand = Math.max(
                elasticsearchProperties.getRecommendRecallSize(),
                elasticsearchProperties.getRecommendExpandRecallSize());
        try {
            List<RecommendationHit> more = elasticsearchRecommendationRepository.knnByVector(pref, expand);
            for (RecommendationHit hit : more) {
                simById.putIfAbsent(hit.activityId(), hit.simScore());
            }
        }
        catch (RuntimeException ex) {
            log.warn("Recommend expand knn failed: {}", ex.getMessage());
        }

        List<Activity> afterExpand = loadFilterCandidates(simById, signedIds, windows);
        if (afterExpand.size() >= elasticsearchProperties.getRecommendMinCandidates()) {
            return;
        }

        double prior = modestSimPrior(simById);
        List<Activity> published = activityRepository.findPublishedByHot(PageRequest.of(0, expand));
        for (Activity a : published) {
            if (a.getId() == null || simById.containsKey(a.getId())) {
                continue;
            }
            if (RecommendationScorer.tagMatchCount(a, interests) <= 0) {
                continue;
            }
            simById.putIfAbsent(a.getId(), prior);
        }
    }

    private static double modestSimPrior(Map<Long, Double> simById) {
        if (simById.isEmpty()) {
            return 0.85;
        }
        double min = simById.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.85);
        return Math.max(0.0, min - 0.02);
    }

    private List<RecommendationHit> coldStartCandidates(int size) {
        List<Activity> hot = activityRepository.findPublishedByHot(PageRequest.of(0, Math.max(1, size)));
        List<RecommendationHit> hits = new ArrayList<>();
        for (Activity a : hot) {
            hits.add(new RecommendationHit(a.getId(), 0.0));
        }
        return hits;
    }
}
