package com.mediparse.search;

import com.mediparse.document.DocumentType;

import java.time.Instant;
import java.util.UUID;

public record SearchQuery(
        String text,
        DocumentType documentType,
        UUID patientId,
        Instant fromDate,
        Instant toDate,
        int page,
        int size
) {
}
