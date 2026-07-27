package com.example.demo.community.client;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ClusteringContracts {

    public static final String ALGORITHM = "KMEANS";
    public static final int RANDOM_STATE = 42;
    public static final String FEATURE_SCHEMA_V2 = "community-features-v2";

    private ClusteringContracts() {
    }

    public record Request(
            String runId,
            String version,
            String algorithm,
            Integer clusterCount,
            Integer randomState,
            String featureSchemaVersion,
            List<FeatureSample> samples
    ) {
        public Request {
            runId = requiredText(runId, "runId");
            version = requiredText(version, "version");
            require(ALGORITHM.equals(algorithm), "algorithm 必须为 KMEANS");
            require(randomState != null && randomState == RANDOM_STATE, "randomState 必须为 42");
            featureSchemaVersion = requiredText(featureSchemaVersion, "featureSchemaVersion");
            samples = List.copyOf(Objects.requireNonNull(samples, "samples 不能为空"));
            require(clusterCount != null && clusterCount >= 2 && clusterCount <= samples.size(),
                    "clusterCount 必须在 2 和样本数之间");
            Set<String> userIds = new HashSet<>();
            require(samples.stream().allMatch(sample -> userIds.add(sample.userId())), "样本 userId 不能重复");
        }
    }

    public record FeatureSample(
            String userId,
            List<String> interests,
            String college,
            String grade,
            List<String> availableTime,
            Integer signupCount,
            Integer approvedSignupCount,
            Integer favoriteCount,
            Integer checkInCount,
            Integer feedbackCount,
            Double averageRating,
            Map<String, Integer> categoryParticipationCounts
    ) {
        public FeatureSample {
            userId = requiredText(userId, "userId");
            interests = immutableStrings(interests, "interests");
            availableTime = immutableStrings(availableTime, "availableTime");
            requireNonNegative(signupCount, "signupCount");
            requireNonNegative(approvedSignupCount, "approvedSignupCount");
            require(approvedSignupCount <= signupCount, "approvedSignupCount 不能超过 signupCount");
            requireNonNegative(favoriteCount, "favoriteCount");
            requireNonNegative(checkInCount, "checkInCount");
            requireNonNegative(feedbackCount, "feedbackCount");
            if (averageRating != null) {
                require(Double.isFinite(averageRating) && averageRating >= 1.0 && averageRating <= 5.0,
                        "averageRating 必须为 1 到 5 的有限数");
            }
            Objects.requireNonNull(categoryParticipationCounts, "categoryParticipationCounts 不能为空");
            LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
            categoryParticipationCounts.forEach((category, count) -> {
                String checkedCategory = requiredText(category, "活动类别");
                requireNonNegative(count, "活动类别计数");
                counts.put(checkedCategory, count);
            });
            categoryParticipationCounts = Map.copyOf(counts);
        }
    }

    public record Response(
            String runId,
            String version,
            String algorithm,
            Integer clusterCount,
            Integer sampleCount,
            Metrics metrics,
            List<CommunitySummary> communities,
            List<MemberResult> members
    ) {
        public Response {
            runId = requiredText(runId, "runId");
            version = requiredText(version, "version");
            require(ALGORITHM.equals(algorithm), "algorithm 必须为 KMEANS");
            require(clusterCount != null && clusterCount >= 2, "clusterCount 至少为 2");
            require(sampleCount != null && sampleCount >= 2, "sampleCount 至少为 2");
            metrics = Objects.requireNonNull(metrics, "metrics 不能为空");
            communities = List.copyOf(Objects.requireNonNull(communities, "communities 不能为空"));
            members = List.copyOf(Objects.requireNonNull(members, "members 不能为空"));
            Set<String> userIds = new HashSet<>();
            require(members.stream().allMatch(member -> userIds.add(member.userId())), "成员 userId 不能重复");
        }
    }

    public record Metrics(Double inertia, List<Double> pcaExplainedVarianceRatio) {
        public Metrics {
            require(inertia != null && Double.isFinite(inertia) && inertia >= 0.0, "inertia 非法");
            pcaExplainedVarianceRatio = List.copyOf(
                    Objects.requireNonNull(pcaExplainedVarianceRatio, "PCA 指标不能为空")
            );
            require(pcaExplainedVarianceRatio.size() == 2, "PCA 指标长度必须为 2");
            require(pcaExplainedVarianceRatio.stream().allMatch(
                    value -> value != null && Double.isFinite(value) && value >= 0.0 && value <= 1.0
            ), "PCA 指标非法");
        }
    }

    public record CommunitySummary(Integer clusterNo, Integer memberCount, List<String> topInterests) {
        public CommunitySummary {
            require(clusterNo != null && clusterNo >= 0, "clusterNo 非法");
            require(memberCount != null && memberCount > 0, "memberCount 非法");
            topInterests = immutableStrings(topInterests, "topInterests");
            require(topInterests.size() <= 3, "topInterests 最多 3 项");
        }
    }

    public record MemberResult(
            String userId,
            Integer clusterNo,
            Double coordinateX,
            Double coordinateY,
            Double distanceToCenter
    ) {
        public MemberResult {
            userId = requiredText(userId, "userId");
            require(clusterNo != null && clusterNo >= 0, "clusterNo 非法");
            requireCoordinate(coordinateX, "coordinateX");
            requireCoordinate(coordinateY, "coordinateY");
            require(distanceToCenter != null && Double.isFinite(distanceToCenter) && distanceToCenter >= 0.0,
                    "distanceToCenter 非法");
        }
    }

    public record HealthResponse(String status, String service, List<String> supportedFeatureSchemas) {
        public HealthResponse {
            require("UP".equals(status), "status 必须为 UP");
            require("clustering-service".equals(service), "service 名称非法");
            supportedFeatureSchemas = immutableStrings(supportedFeatureSchemas, "supportedFeatureSchemas");
        }
    }

    public record ErrorResponse(String code, String message, Map<String, Object> details) {
        public ErrorResponse {
            code = requiredText(code, "code");
            message = requiredText(message, "message");
            details = Map.copyOf(Objects.requireNonNull(details, "details 不能为空"));
        }
    }

    private static List<String> immutableStrings(List<String> values, String field) {
        List<String> copy = List.copyOf(Objects.requireNonNull(values, field + " 不能为空"));
        require(copy.stream().allMatch(value -> value != null && !value.isBlank()), field + " 包含空值");
        return copy;
    }

    private static String requiredText(String value, String field) {
        require(value != null && !value.isBlank(), field + " 不能为空");
        return value;
    }

    private static void requireNonNegative(Integer value, String field) {
        require(value != null && value >= 0, field + " 必须为非负整数");
    }

    private static void requireCoordinate(Double value, String field) {
        require(value != null && Double.isFinite(value) && value >= 0.0 && value <= 100.0, field + " 非法");
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }
}
