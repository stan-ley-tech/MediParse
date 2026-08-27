package com.mediparse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mediparse.opensearch")
public record OpenSearchProperties(
        String host,
        int port,
        String scheme,
        String username,
        String password,
        String documentsIndex
) {
}
