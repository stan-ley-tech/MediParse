package com.mediparse.document;

import java.time.Instant;

public record DownloadUrlResponse(String url, Instant expiresAt) {
}
