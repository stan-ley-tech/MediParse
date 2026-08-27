package com.mediparse.processing;

import java.util.UUID;

public record JobMessage(UUID jobId, UUID documentId) {
}
