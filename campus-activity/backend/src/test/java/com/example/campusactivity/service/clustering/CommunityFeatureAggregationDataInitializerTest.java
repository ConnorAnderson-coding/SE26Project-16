package com.example.campusactivity.service.clustering;

import com.example.campusactivity.client.clustering.ClusteringClient;
import com.example.campusactivity.client.clustering.dto.FeatureSample;
import com.example.campusactivity.repository.ClusteringRunRepository;
import com.example.campusactivity.repository.CommunityMemberRepository;
import com.example.campusactivity.repository.CommunityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "community-clustering.python.enabled=false")
class CommunityFeatureAggregationDataInitializerTest {
    @Autowired
    private CommunityFeatureAggregationService service;
    @Autowired
    private ClusteringRunRepository runRepository;
    @Autowired
    private CommunityRepository communityRepository;
    @Autowired
    private CommunityMemberRepository memberRepository;
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void aggregatesDataInitializerSnapshotWithoutCallingPythonOrWritingClusteringResults() {
        long runCount = runRepository.count();
        long communityCount = communityRepository.count();
        long memberCount = memberRepository.count();

        FeatureAggregationResult result = service.aggregateFeatureSamples();

        assertThat(result.samples()).extracting(FeatureSample::userId)
                .containsExactly("524030910001", "524030910002", "T001");

        FeatureSample first = sample(result, "524030910001");
        assertThat(first.signupCount()).isEqualTo(2);
        assertThat(first.approvedSignupCount()).isEqualTo(1);
        assertThat(first.favoriteCount()).isEqualTo(2);
        assertThat(first.checkInCount()).isEqualTo(1);
        assertThat(first.feedbackCount()).isEqualTo(1);
        assertThat(first.averageRating()).isEqualTo(5.0);
        assertThat(first.categoryParticipationCounts()).containsExactly(
                java.util.Map.entry("academic", 1),
                java.util.Map.entry("club", 0)
        );

        FeatureSample second = sample(result, "524030910002");
        assertThat(second.signupCount()).isEqualTo(2);
        assertThat(second.approvedSignupCount()).isEqualTo(1);
        assertThat(second.favoriteCount()).isEqualTo(1);
        assertThat(second.checkInCount()).isZero();
        assertThat(second.feedbackCount()).isZero();
        assertThat(second.averageRating()).isNull();
        assertThat(second.categoryParticipationCounts()).containsExactly(
                java.util.Map.entry("academic", 0),
                java.util.Map.entry("club", 1)
        );

        FeatureSample teacher = sample(result, "T001");
        assertThat(teacher.signupCount()).isZero();
        assertThat(teacher.approvedSignupCount()).isZero();
        assertThat(teacher.favoriteCount()).isZero();
        assertThat(teacher.checkInCount()).isZero();
        assertThat(teacher.feedbackCount()).isZero();
        assertThat(teacher.averageRating()).isNull();
        assertThat(teacher.categoryParticipationCounts()).containsExactly(
                java.util.Map.entry("academic", 0),
                java.util.Map.entry("club", 0)
        );

        assertThat(List.copyOf(first.categoryParticipationCounts().keySet()))
                .containsExactly("academic", "club");
        assertThat(result.diagnostics()).isEqualTo(new FeatureAggregationDiagnostics(
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L
        ));
        assertThat(applicationContext.getBeansOfType(ClusteringClient.class)).isEmpty();
        assertThat(runRepository.count()).isEqualTo(runCount);
        assertThat(communityRepository.count()).isEqualTo(communityCount);
        assertThat(memberRepository.count()).isEqualTo(memberCount);
    }

    private static FeatureSample sample(FeatureAggregationResult result, String userId) {
        return result.samples().stream()
                .filter(sample -> sample.userId().equals(userId))
                .findFirst()
                .orElseThrow();
    }
}
