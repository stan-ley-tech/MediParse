package com.mediparse.search;

import java.time.Instant;

public record SearchHit(
        String documentId,
        String patientId,
        String patientName,
        String documentType,
        String status,
        String originalFilename,
        Instant createdAt,
        double score
) {
    static SearchHit from(IndexedDocument document, double score) {
        return new SearchHit(document.getDocumentId(), document.getPatientId(), document.getPatientName(),
                document.getDocumentType(), document.getStatus(), document.getOriginalFilename(),
                document.getCreatedAt(), score);
    }
}
