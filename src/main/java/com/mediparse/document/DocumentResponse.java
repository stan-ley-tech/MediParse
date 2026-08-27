package com.mediparse.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID patientId,
        UUID uploadedBy,
        String originalFilename,
        String contentType,
        long fileSizeBytes,
        DocumentType documentType,
        DocumentStatus status,
        int versionNumber,
        UUID parentDocumentId,
        UUID versionGroupId,
        String processingError,
        Instant createdAt,
        Instant updatedAt
) {
    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getPatientId(),
                document.getUploadedBy(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getFileSizeBytes(),
                document.getDocumentType(),
                document.getStatus(),
                document.getVersionNumber(),
                document.getParentDocumentId(),
                document.getVersionGroupId(),
                document.getProcessingError(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
