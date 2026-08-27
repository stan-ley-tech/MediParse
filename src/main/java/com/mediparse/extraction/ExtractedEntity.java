package com.mediparse.extraction;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "extracted_entities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExtractedEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private EntityType entityType;

    @Column(nullable = false)
    private String label;

    private String value;

    @Column(name = "numeric_value")
    private BigDecimal numericValue;

    private String unit;

    @Column(name = "reference_range")
    private String referenceRange;

    @Enumerated(EnumType.STRING)
    private ResultStatus status;

    private BigDecimal confidence = BigDecimal.ONE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ExtractedEntity(UUID documentId, EntityType entityType, String label, String value,
                            BigDecimal numericValue, String unit, String referenceRange,
                            ResultStatus status, BigDecimal confidence) {
        this.documentId = documentId;
        this.entityType = entityType;
        this.label = label;
        this.value = value;
        this.numericValue = numericValue;
        this.unit = unit;
        this.referenceRange = referenceRange;
        this.status = status;
        this.confidence = confidence != null ? confidence : BigDecimal.ONE;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
