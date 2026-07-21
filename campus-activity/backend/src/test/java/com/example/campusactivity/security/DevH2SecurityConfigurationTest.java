package com.example.campusactivity.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "community-clustering.python.enabled=false")
@ActiveProfiles("dev")
class DevH2SecurityConfigurationTest {
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private Environment environment;

    @Test
    void devProfileEnablesH2AndAddsOnlyItsDedicatedHigherPriorityChain() {
        assertThat(environment.getProperty(
                "spring.h2.console.enabled",
                Boolean.class
        )).isTrue();
        assertThat(applicationContext.getBeansOfType(SecurityFilterChain.class))
                .hasSize(2)
                .containsKeys(
                        "h2ConsoleSecurityFilterChain",
                        "applicationSecurityFilterChain"
                );
    }
}
