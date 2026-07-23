package com.example.demo.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "community-clustering.python")
public record ClusteringServiceProperties(
        boolean enabled,
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    public ClusteringServiceProperties {
        baseUrl = baseUrl == null ? URI.create("http://127.0.0.1:8000") : baseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
    }

    @AssertTrue(message = "聚类服务必须使用无凭据、无查询参数的 HTTP(S) 根地址")
    public boolean isBaseUrlValid() {
        return !enabled || (baseUrl.isAbsolute()
                && ALLOWED_SCHEMES.contains(baseUrl.getScheme())
                && baseUrl.getHost() != null
                && baseUrl.getUserInfo() == null
                && baseUrl.getQuery() == null
                && baseUrl.getFragment() == null
                && (baseUrl.getPath().isEmpty() || "/".equals(baseUrl.getPath())));
    }

    @AssertTrue(message = "聚类服务超时必须为正数且不超过 24 小时")
    public boolean isTimeoutsValid() {
        Duration maximum = Duration.ofHours(24);
        return !connectTimeout.isZero() && !connectTimeout.isNegative()
                && !readTimeout.isZero() && !readTimeout.isNegative()
                && connectTimeout.compareTo(maximum) <= 0
                && readTimeout.compareTo(maximum) <= 0;
    }
}
