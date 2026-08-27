package com.mediparse.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mediparse.jwt")
public record JwtProperties(
        String secret,
        long accessTokenTtlMinutes,
        long refreshTokenTtlMinutes
) {
}
