package com.mediparse.document;

public record StoredFile(String relativePath, long sizeBytes, String sha256Hash) {
}
