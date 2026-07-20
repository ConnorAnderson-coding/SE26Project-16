package com.example.campusactivity.security;

import com.example.campusactivity.repository.UserRepository;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "campus.demo-password=TestPassword123!",
        "community-clustering.python.enabled=false"
})
class SecurityContextConfigurationTest {
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private Environment environment;

    @Test
    void defaultContextHasOneSharedSecurityComponentSetAndNoH2Chain() {
        assertThat(applicationContext.getBeansOfType(PasswordEncoder.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(
                HttpSessionSecurityContextRepository.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(
                HttpSessionCsrfTokenRepository.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(SecurityFilterChain.class))
                .hasSize(1);
        assertThat(environment.getProperty(
                "spring.h2.console.enabled",
                Boolean.class
        )).isFalse();
    }

    @Test
    void loginFilterUsesOneDisabledServletRegistrationAndRemainsInSecurityChain() {
        JsonUsernamePasswordAuthenticationFilter loginFilter =
                applicationContext.getBean(
                        JsonUsernamePasswordAuthenticationFilter.class
                );
        assertThat(applicationContext.getBeansOfType(
                JsonUsernamePasswordAuthenticationFilter.class))
                .hasSize(1)
                .containsValue(loginFilter);

        FilterRegistrationBean<?> registration =
                (FilterRegistrationBean<?>) applicationContext.getBean(
                        "jsonLoginFilterRegistration"
                );
        assertThat(registration.getFilter()).isSameAs(loginFilter);
        assertThat(registration.isEnabled()).isFalse();

        boolean hasEnabledServletRegistration = applicationContext
                .getBeansOfType(FilterRegistrationBean.class)
                .values()
                .stream()
                .anyMatch(candidate -> candidate.isEnabled()
                        && candidate.getFilter() == loginFilter);
        assertThat(hasEnabledServletRegistration).isFalse();

        SecurityFilterChain securityFilterChain = applicationContext.getBean(
                "applicationSecurityFilterChain",
                SecurityFilterChain.class
        );
        List<Filter> filters = securityFilterChain.getFilters();
        assertThat(filters).contains(loginFilter);
        assertThat(filters.stream().map(Object::getClass).toList())
                .containsSubsequence(
                        CsrfFilter.class,
                        JsonUsernamePasswordAuthenticationFilter.class,
                        AuthorizationFilter.class
                );
    }

    @Test
    void initializerKeepsOriginalUsersAndStoresOnlyDelegatingHashes() {
        assertThat(userRepository.findAll())
                .extracting(user -> user.getId())
                .containsExactlyInAnyOrder(
                        "524030910001",
                        "524030910002",
                        "T001"
                );
        assertThat(userRepository.findAll())
                .allSatisfy(user -> {
                    assertThat(user.getPassword()).startsWith("{bcrypt}");
                    assertThat(passwordEncoder.matches(
                            "TestPassword123!",
                            user.getPassword()
                    )).isTrue();
                });
        assertThat(userRepository.findAll())
                .noneMatch(user -> "admin".equals(user.getRole()));
    }
}
