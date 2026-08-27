package com.mediparse.extraction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntityNormalizerTest {

    private final EntityNormalizer normalizer = new EntityNormalizer();

    @Test
    void derivesNormalWhenValueWithinRange() {
        var draft = labResult("13.4", "12-16", null);

        var normalized = normalizer.normalize(List.of(draft));

        assertThat(normalized.get(0).status()).isEqualTo(ResultStatus.NORMAL);
    }

    @Test
    void derivesHighWhenValueAboveRange() {
        var draft = labResult("18.0", "12-16", null);

        var normalized = normalizer.normalize(List.of(draft));

        assertThat(normalized.get(0).status()).isEqualTo(ResultStatus.HIGH);
    }

    @Test
    void derivesLowWhenValueBelowRange() {
        var draft = labResult("9.0", "12-16", null);

        var normalized = normalizer.normalize(List.of(draft));

        assertThat(normalized.get(0).status()).isEqualTo(ResultStatus.LOW);
    }

    @Test
    void leavesExplicitStatusUntouched() {
        var draft = labResult("18.0", "12-16", ResultStatus.ABNORMAL);

        var normalized = normalizer.normalize(List.of(draft));

        assertThat(normalized.get(0).status()).isEqualTo(ResultStatus.ABNORMAL);
    }

    @Test
    void leavesNonLabResultEntitiesUnchanged() {
        var draft = new ExtractedEntityDraft(EntityType.PATIENT, "John Kamau", null, null, null, null, null);

        var normalized = normalizer.normalize(List.of(draft));

        assertThat(normalized.get(0)).isEqualTo(draft);
    }

    private ExtractedEntityDraft labResult(String value, String range, ResultStatus status) {
        return new ExtractedEntityDraft(EntityType.LAB_RESULT, "Hemoglobin", value,
                new BigDecimal(value), "g/dL", range, status);
    }
}
