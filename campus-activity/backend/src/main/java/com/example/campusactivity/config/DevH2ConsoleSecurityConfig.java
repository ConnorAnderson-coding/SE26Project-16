package com.example.campusactivity.config;

import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("dev")
public class DevH2ConsoleSecurityConfig {
    @Bean
    @Order(1)
    public SecurityFilterChain h2ConsoleSecurityFilterChain(
            HttpSecurity http
    ) throws Exception {
        http
                .securityMatcher(PathRequest.toH2Console())
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(PathRequest.toH2Console()))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin()))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }
}
