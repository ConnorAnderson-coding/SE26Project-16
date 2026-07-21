package com.example.campusactivity.security;

import com.example.campusactivity.dto.auth.LoginRequest;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Validator;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.io.IOException;

public final class JsonUsernamePasswordAuthenticationFilter
        extends AbstractAuthenticationProcessingFilter {
    private final ObjectMapper authObjectMapper;
    private final Validator validator;
    private final SecurityJsonResponseWriter responseWriter;

    public JsonUsernamePasswordAuthenticationFilter(
            ObjectMapper objectMapper,
            Validator validator,
            SecurityJsonResponseWriter responseWriter
    ) {
        super(new AntPathRequestMatcher("/api/auth/login", "POST"));
        this.authObjectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.validator = validator;
        this.responseWriter = responseWriter;
    }

    @Override
    public Authentication attemptAuthentication(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws AuthenticationException, IOException {
        Authentication current =
                SecurityContextHolder.getContext().getAuthentication();
        if (current != null
                && current.isAuthenticated()
                && !(current instanceof AnonymousAuthenticationToken)) {
            responseWriter.writeError(
                    response,
                    SecurityErrorCode.INVALID_AUTH_REQUEST
            );
            return null;
        }

        if (!isJson(request.getContentType())) {
            throw new InvalidAuthRequestAuthenticationException();
        }

        final LoginRequest loginRequest;
        try {
            loginRequest = authObjectMapper.readValue(
                    request.getInputStream(),
                    LoginRequest.class
            );
        } catch (RuntimeException | IOException _exception) {
            throw new InvalidAuthRequestAuthenticationException();
        }
        if (!validator.validate(loginRequest).isEmpty()) {
            throw new InvalidAuthRequestAuthenticationException();
        }

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        loginRequest.id(),
                        loginRequest.password()
                );
        return getAuthenticationManager().authenticate(authentication);
    }

    private static boolean isJson(String contentTypeValue) {
        if (contentTypeValue == null || contentTypeValue.isBlank()) {
            return false;
        }
        try {
            MediaType contentType = MediaType.parseMediaType(contentTypeValue);
            return MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                    || contentType.getSubtype().endsWith("+json");
        } catch (IllegalArgumentException _exception) {
            return false;
        }
    }
}
