package com.mediparse.document;

import java.util.UUID;

public record SignedDownload(UUID documentId, long expiresAt, String signature) {
}
