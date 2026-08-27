package com.mediparse.extraction;

import java.math.BigDecimal;

/** An entity as parsed from raw text, before normalization or persistence. */
public record ExtractedEntityDraft(
        EntityType entityType,
        String label,
        String value,
        BigDecimal numericValue,
        String unit,
        String referenceRange,
        ResultStatus status
) {
    public ExtractedEntityDraft withStatus(ResultStatus newStatus) {
        return new ExtractedEntityDraft(entityType, label, value, numericValue, unit, referenceRange, newStatus);
    }
}
