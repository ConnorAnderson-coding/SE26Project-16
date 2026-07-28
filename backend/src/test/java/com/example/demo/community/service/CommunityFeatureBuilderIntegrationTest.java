package com.example.demo.community.service;

import com.example.demo.community.client.ClusteringContracts.FeatureSample;
import com.example.demo.entity.Registration;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class CommunityFeatureBuilderIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private CommunityFeatureBuilder featureBuilder;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Test
    void executesJpaAggregateProjectionsAgainstMainSchema() {
        TestScenario scenario = createScenario();
        Registration registration = new Registration();
        registration.setActivity(scenario.activity());
        registration.setUser(scenario.student());
        registration.setStatus("approved");
        registration.setCreatedAt(LocalDateTime.now().minusDays(2));
        registrationRepository.saveAndFlush(registration);

        CommunityFeatureSnapshot snapshot = featureBuilder.build();

        FeatureSample student = snapshot.samples().stream()
                .filter(sample -> sample.userId().equals(scenario.student().getId()))
                .findFirst()
                .orElseThrow();
        assertThat(student.signupCount()).isEqualTo(1);
        assertThat(student.approvedSignupCount()).isEqualTo(1);
        assertThat(student.categoryParticipationCounts())
                .containsEntry(scenario.activity().getCategory(), 1);
        assertThat(snapshot.samples()).extracting(FeatureSample::userId)
                .doesNotContain(scenario.admin().getId());
    }
}
