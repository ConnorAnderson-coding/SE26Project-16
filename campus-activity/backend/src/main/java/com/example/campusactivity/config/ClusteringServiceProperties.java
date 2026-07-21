package com.example.campusactivity.config;

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
    public static final Duration MIN_CLIENT_TIMEOUT = Duration.ofMillis(1);
    public static final Duration MAX_CLIENT_TIMEOUT = Duration.ofMillis(Integer.MAX_VALUE);

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    public ClusteringServiceProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
    }

    @AssertTrue(message = "Python 聚类服务启用时必须配置合法的 HTTP(S) base-url")
    public boolean isBaseUrlValid() {
        if (!enabled) {
            return true;
        }
        return baseUrl != null
                && baseUrl.isAbsolute()
                && ALLOWED_SCHEMES.contains(baseUrl.getScheme())
                && baseUrl.getHost() != null
                && baseUrl.getUserInfo() == null
                && baseUrl.getQuery() == null
                && baseUrl.getFragment() == null
                && (baseUrl.getPort() == -1
                || (baseUrl.getPort() >= 1 && baseUrl.getPort() <= 65535))
                && (baseUrl.getPath().isEmpty() || "/".equals(baseUrl.getPath()));
    }

    @AssertTrue(message = "Python 聚类服务连接和读取超时必须在 1 毫秒至 Integer.MAX_VALUE 毫秒之间")
    public boolean isTimeoutsValid() {
        return isClientTimeoutValid(connectTimeout)
                && isClientTimeoutValid(readTimeout);
    }

    private static boolean isClientTimeoutValid(Duration timeout) {
        return timeout.compareTo(MIN_CLIENT_TIMEOUT) >= 0
                && timeout.compareTo(MAX_CLIENT_TIMEOUT) <= 0;
    }
}
