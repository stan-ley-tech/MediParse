package com.mediparse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mediparse.processing")
public record ProcessingProperties(
        int maxAttempts,
        int textExtractionCharLimit
) {
}
