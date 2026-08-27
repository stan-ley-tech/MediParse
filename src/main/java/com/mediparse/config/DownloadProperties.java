package com.mediparse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mediparse.download")
public record DownloadProperties(
        String signingSecret,
        long urlTtlSeconds
) {
}
