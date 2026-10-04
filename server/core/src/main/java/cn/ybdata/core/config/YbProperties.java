package cn.ybdata.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param analyticsUrl base URL of the Python analytics service
 * @param seedOverwrite re-import page seeds on every start (dev convenience)
 */
@ConfigurationProperties(prefix = "yb")
public record YbProperties(String analyticsUrl, boolean seedOverwrite) {
    public YbProperties {
        if (analyticsUrl == null || analyticsUrl.isBlank()) analyticsUrl = "http://localhost:8090";
    }
}
