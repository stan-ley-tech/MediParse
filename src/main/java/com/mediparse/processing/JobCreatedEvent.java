package com.mediparse.processing;

import java.util.UUID;

record JobCreatedEvent(UUID jobId, UUID documentId) {
}
