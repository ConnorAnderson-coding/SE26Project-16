package com.example.demo.recommend;

import com.example.demo.config.ElasticsearchProperties;
import com.example.demo.entity.Registration;
import com.example.demo.entity.User;
import com.example.demo.recommend.repository.ElasticsearchRecommendationRepository;
import com.example.demo.recommend.support.VectorMath;
import com.example.demo.repository.RegistrationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class UserPreferenceVectorService {

    private static final String REDIS_KEY_PREFIX = "campus:user:pref_vector:";
    private static final Duration CACHE_TTL = Duration.ofHours(6);

    private final RegistrationRepository registrationRepository;
    private final ElasticsearchRecommendationRepository elasticsearchRecommendationRepository;
    private final ElasticsearchProperties elasticsearchProperties;
    private final ObjectProvider<StringRedisTemplate> stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public float[] getOrBuild(User user) {
        if (user == null || user.getId() == null) {
            return null;
        }
        float[] cached = readCache(user.getId());
        if (cached != null) {
            return cached;
        }
        float[] built = build(user);
        if (built != null) {
            writeCache(user.getId(), built);
        }
        return built;
    }

    public void invalidate(String userId) {
        if (userId == null) {
            return;
        }
        StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
        if (redis != null) {
            redis.delete(REDIS_KEY_PREFIX + userId);
        }
    }

    float[] build(User user) {
        float[] interestVec = buildInterestVector(user);
        float[] historyVec = buildHistoryVector(user.getId());
        if (interestVec == null && historyVec == null) {
            return null;
        }
        if (interestVec == null) {
            return historyVec;
        }
        if (historyVec == null) {
            return interestVec;
        }
        return VectorMath.convexCombine(
                interestVec,
                historyVec,
                elasticsearchProperties.getPrefInterestMix());
    }

    private float[] buildInterestVector(User user) {
        List<String> interests = user.getInterests();
        if (interests == null || interests.isEmpty()) {
            return null;
        }
        String text = interests.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
        if (text.isBlank()) {
            return null;
        }
        float[] raw = elasticsearchRecommendationRepository.inferTextEmbedding(text);
        return raw == null ? null : VectorMath.normalize(raw);
    }

    private float[] buildHistoryVector(String userId) {
        int k = Math.max(1, elasticsearchProperties.getPrefHistorySize());
        List<Registration> regs = registrationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(r -> r.getStatus() == null || !"rejected".equals(r.getStatus()))
                .limit(k)
                .toList();
        if (regs.isEmpty()) {
            return null;
        }
        List<Long> ids = regs.stream()
                .map(r -> r.getActivity().getId())
                .toList();
        Map<Long, float[]> embeddings = elasticsearchRecommendationRepository.mgetEmbeddings(ids);

        List<float[]> vectors = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        LocalDate today = LocalDate.now();
        double halfLife = elasticsearchProperties.getPrefHalfLifeDays();
        for (Registration reg : regs) {
            Long aid = reg.getActivity().getId();
            float[] emb = embeddings.get(aid);
            if (emb == null) {
                continue;
            }
            LocalDate day = reg.getCreatedAt() != null
                    ? reg.getCreatedAt().toLocalDate()
                    : today;
            long daysAgo = ChronoUnit.DAYS.between(day, today);
            vectors.add(emb);
            weights.add(VectorMath.timeDecayWeight(daysAgo, halfLife));
        }
        return VectorMath.weightedAverage(vectors, weights);
    }

    private float[] readCache(String userId) {
        StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
        if (redis == null) {
            return null;
        }
        String json = redis.opsForValue().get(REDIS_KEY_PREFIX + userId);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            float[] arr = objectMapper.readValue(json, float[].class);
            return arr != null && arr.length > 0 ? arr : null;
        }
        catch (JsonProcessingException ex) {
            log.warn("Corrupt pref vector cache for {}", userId);
            redis.delete(REDIS_KEY_PREFIX + userId);
            return null;
        }
    }

    private void writeCache(String userId, float[] vector) {
        StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(
                    REDIS_KEY_PREFIX + userId,
                    objectMapper.writeValueAsString(vector),
                    CACHE_TTL);
        }
        catch (JsonProcessingException ex) {
            log.warn("Failed to cache pref vector for {}", userId);
        }
    }
}
