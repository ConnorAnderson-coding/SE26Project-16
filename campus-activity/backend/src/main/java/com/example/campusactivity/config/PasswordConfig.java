package com.example.campusactivity.config;

import com.example.campusactivity.security.StrictDelegatingPasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new StrictDelegatingPasswordEncoder();
    }
}
