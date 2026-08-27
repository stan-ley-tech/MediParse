package com.mediparse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "mediparse.storage")
public record StorageProperties(
        String rootPath,
        long maxFileSizeBytes,
        List<String> allowedExtensions
) {
}
