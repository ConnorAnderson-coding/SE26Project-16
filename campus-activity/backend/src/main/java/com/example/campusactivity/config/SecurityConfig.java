package com.example.campusactivity.config;

import com.example.campusactivity.security.AuthenticatedLogoutRequestMatcher;
import com.example.campusactivity.security.CampusUserDetailsService;
import com.example.campusactivity.security.JsonAccessDeniedHandler;
import com.example.campusactivity.security.JsonAuthenticationEntryPoint;
import com.example.campusactivity.security.JsonAuthenticationFailureHandler;
import com.example.campusactivity.security.JsonAuthenticationSuccessHandler;
import com.example.campusactivity.security.JsonLogoutSuccessHandler;
import com.example.campusactivity.security.JsonUsernamePasswordAuthenticationFilter;
import com.example.campusactivity.security.SecurityJsonResponseWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    public HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public HttpSessionCsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(
            HttpSessionCsrfTokenRepository csrfTokenRepository
    ) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)
        ));
    }

    @Bean
    public AuthenticationManager authenticationManager(
            CampusUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public JsonUsernamePasswordAuthenticationFilter jsonLoginFilter(
            ObjectMapper objectMapper,
            Validator validator,
            SecurityJsonResponseWriter responseWriter,
            AuthenticationManager authenticationManager,
            HttpSessionSecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            JsonAuthenticationSuccessHandler successHandler,
            JsonAuthenticationFailureHandler failureHandler
    ) {
        JsonUsernamePasswordAuthenticationFilter filter =
                new JsonUsernamePasswordAuthenticationFilter(
                        objectMapper,
                        validator,
                        responseWriter
                );
        filter.setAuthenticationManager(authenticationManager);
        filter.setSecurityContextRepository(securityContextRepository);
        filter.setSessionAuthenticationStrategy(sessionAuthenticationStrategy);
        filter.setAuthenticationSuccessHandler(successHandler);
        filter.setAuthenticationFailureHandler(failureHandler);
        return filter;
    }

    @Bean
    public FilterRegistrationBean<JsonUsernamePasswordAuthenticationFilter>
            jsonLoginFilterRegistration(
                    JsonUsernamePasswordAuthenticationFilter jsonLoginFilter
            ) {
        FilterRegistrationBean<JsonUsernamePasswordAuthenticationFilter>
                registration = new FilterRegistrationBean<>(jsonLoginFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http,
            JsonUsernamePasswordAuthenticationFilter jsonLoginFilter,
            HttpSessionSecurityContextRepository securityContextRepository,
            HttpSessionCsrfTokenRepository csrfTokenRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            JsonLogoutSuccessHandler logoutSuccessHandler
    ) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .withObjectPostProcessor(
                                new ObjectPostProcessor<CsrfFilter>() {
                                    @Override
                                    public <O extends CsrfFilter> O postProcess(
                                            O csrfFilter
                                    ) {
                                        csrfFilter.setAccessDeniedHandler(
                                                accessDeniedHandler
                                        );
                                        return csrfFilter;
                                    }
                                }
                        ))
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionAuthenticationStrategy(sessionAuthenticationStrategy))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout
                        .logoutRequestMatcher(new AuthenticatedLogoutRequestMatcher())
                        .addLogoutHandler(new CsrfLogoutHandler(csrfTokenRepository))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(logoutSuccessHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .addFilterAt(
                        jsonLoginFilter,
                        UsernamePasswordAuthenticationFilter.class
                );
        return http.build();
    }
}
