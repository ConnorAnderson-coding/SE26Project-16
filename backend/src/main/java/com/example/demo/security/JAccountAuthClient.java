package com.example.demo.security;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.demo.common.BusinessException;
import com.example.demo.config.JAccountProperties;
import com.example.demo.dto.response.JAccountTokenResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class JAccountAuthClient {

    private final JAccountProperties properties;

    public URI buildAuthorizationUri(String state) {
        ensureConfigured();
        URI uri = UriComponentsBuilder.fromUriString(properties.getAuthorizationUri())
                .queryParam("response_type", "code")
                .queryParam("scope", properties.getScope())
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("state", state)
                .build()
                .toUri();
        log.info("jAccount authorization request prepared: clientId={}, scope={}, redirectUri={}, authorizationUri={}",
                properties.getClientId(), properties.getScope(), properties.getRedirectUri(), properties.getAuthorizationUri());
        return uri;
    }

    public URI buildLogoutUri(String state) {
        ensureConfigured();
        return UriComponentsBuilder.fromUriString(properties.getLogoutUri())
                .queryParam("client_id", properties.getClientId())
                .queryParam("post_logout_redirect_uri", properties.getFrontendLogoutUri())
                .queryParam("state", state)
                .build()
                .toUri();
    }

    public JAccountTokenResponse exchangeCode(String code) {
        ensureConfigured();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", properties.getRedirectUri());
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());

        log.info("jAccount token request prepared: tokenUri={}, clientId={}, secretLength={}, secretSha256Prefix={}, "
                        + "redirectUri={}, codeLength={}, authMethod=form",
                properties.getTokenUri(),
                properties.getClientId(),
                safeLength(properties.getClientSecret()),
                sha256Prefix(properties.getClientSecret()),
                properties.getRedirectUri(),
                safeLength(code));

        JAccountTokenResponse response;
        try {
            response = RestClient.create()
                    .post()
                    .uri(properties.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                        String body = new String(clientResponse.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.warn("jAccount token request failed: status={}, responseBody={}",
                                clientResponse.getStatusCode(), body);
                        throw new BusinessException("jAccount 换取访问令牌失败: " + body);
                    })
                    .body(JAccountTokenResponse.class);
        } catch (HttpStatusCodeException ex) {
            log.warn("jAccount token request failed: status={}, responseBody={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new BusinessException("jAccount 换取访问令牌失败: " + ex.getResponseBodyAsString());
        }
        if (response == null || !StringUtils.hasText(response.accessToken())) {
            throw new BusinessException("jAccount 未返回有效访问令牌");
        }
        log.info("jAccount token request succeeded: tokenType={}, expiresIn={}, hasRefreshToken={}",
                response.tokenType(), response.expiresIn(), StringUtils.hasText(response.refreshToken()));
        return response;
    }

    public JAccountUserInfo fetchProfile(String accessToken) {
        ensureConfigured();
        try {
            URI profileUri = UriComponentsBuilder.fromUriString(properties.getProfileUri())
                    .queryParam("access_token", accessToken)
                    .build()
                    .toUri();
            log.info("jAccount profile request prepared: profileUri={}, accessTokenLength={}",
                    properties.getProfileUri(), safeLength(accessToken));
            Map<String, Object> response = RestClient.create()
                    .get()
                    .uri(profileUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                        String body = new String(clientResponse.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.warn("jAccount profile request failed: status={}, responseBody={}",
                                clientResponse.getStatusCode(), body);
                        throw new BusinessException("jAccount 用户信息获取失败: " + body);
                    })
                    .body(new ParameterizedTypeReference<>() {
                    });
            Map<String, Object> profile = unwrapProfile(response);
            String sub = firstText(profile, "id", "account");
            if (!StringUtils.hasText(sub)) {
                throw new BusinessException("jAccount 用户信息缺少唯一标识");
            }
            log.info("jAccount profile request succeeded: profileKeys={}, resolvedIdLength={}, hasName={}, hasCode={}, type={}",
                    profile.keySet(), safeLength(sub), StringUtils.hasText(firstText(profile, "name")),
                    StringUtils.hasText(firstText(profile, "code", "account")), firstText(profile, "userType", "type"));
            return new JAccountUserInfo(
                    sub,
                    firstText(profile, "name"),
                    firstText(profile, "code", "account"),
                    firstText(profile, "userType", "type"));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("jAccount 用户信息获取失败");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapProfile(Map<String, Object> response) {
        if (response == null) {
            throw new BusinessException("jAccount 用户信息为空");
        }
        Object entities = response.get("entities");
        if (entities instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Map<?, ?> first) {
            return (Map<String, Object>) first;
        }
        return response;
    }

    private String firstText(Map<String, Object> payload, String... names) {
        for (String name : names) {
            Object value = payload.get(name);
            if (value != null && StringUtils.hasText(value.toString())) {
                return value.toString();
            }
        }
        return null;
    }

    private void ensureConfigured() {
        if (!properties.isConfigured()) {
            throw new BusinessException("jAccount 单点登录未配置");
        }
    }

    private int safeLength(String value) {
        return value == null ? -1 : value.length();
    }

    private String sha256Prefix(String value) {
        if (value == null) {
            return "null";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException ex) {
            return "unavailable";
        }
    }
}
