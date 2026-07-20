package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jaccount")
public class JAccountProperties {

    private boolean enabled = false;
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String frontendCallbackUri = "http://localhost:5173/oauth/callback";
    private String frontendLogoutUri = "http://localhost:5173/";
    private String authorizationUri = "https://jaccount.sjtu.edu.cn/oauth2/authorize";
    private String tokenUri = "https://jaccount.sjtu.edu.cn/oauth2/token";
    private String profileUri = "https://api.sjtu.edu.cn/v1/me/profile";
    private String logoutUri = "https://jaccount.sjtu.edu.cn/oauth2/logout";
    private String scope = "profile";
    private boolean stateCookieSecure = false;

    public boolean isConfigured() {
        return enabled
                && StringUtils.hasText(clientId)
                && StringUtils.hasText(clientSecret)
                && StringUtils.hasText(redirectUri);
    }
}
