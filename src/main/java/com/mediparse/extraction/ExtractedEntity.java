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
    @Column(name = "entity_type", nullable = false, length = 32)
    private EntityType entityType;

    @Column(nullable = false, length = 255)
    private String label;

    @Column(length = 512)
    private String value;

    @Column(name = "numeric_value", precision = 12, scale = 4)
    private BigDecimal numericValue;

    @Column(length = 32)
    private String unit;

    @Column(name = "reference_range", length = 64)
    private String referenceRange;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ResultStatus status;

    @Column(nullable = false, precision = 3, scale = 2)
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
