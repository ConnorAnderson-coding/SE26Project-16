package com.example.campusactivity.security;

import com.example.campusactivity.service.auth.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonAuthenticationSuccessHandlerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deletedAccountAfterAuthenticationClearsSessionContextAndCookie()
            throws Exception {
        AuthService authService = mock(AuthService.class);
        when(authService.findAuthenticatedUser("deleted-account"))
                .thenReturn(Optional.empty());
        JsonAuthenticationSuccessHandler handler =
                new JsonAuthenticationSuccessHandler(
                        authService,
                        new AccountSessionInvalidator(),
                        new SecurityJsonResponseWriter(objectMapper)
                );

        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        "deleted-account",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
                );
        SecurityContext securityContext =
                SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(authService).findAuthenticatedUser("deleted-account");
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType())
                .startsWith(MediaType.APPLICATION_JSON_VALUE);

        Cookie sessionCookie = response.getCookie("JSESSIONID");
        assertThat(sessionCookie).isNotNull();
        assertThat(sessionCookie.getMaxAge()).isZero();

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("code", "message", "details");
        assertThat(json.get("code").asText())
                .isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(json.get("message").asText()).isEqualTo("请先登录");
        assertThat(json.get("details").isObject()).isTrue();
        assertThat(json.get("details").isEmpty()).isTrue();
        assertThat(body).doesNotContain(
                "deleted-account",
                "Exception",
                "password",
                "ROLE_"
        );
    }
}
