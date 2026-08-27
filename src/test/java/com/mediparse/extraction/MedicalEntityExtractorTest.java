package com.mediparse.extraction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MedicalEntityExtractorTest {

    private final MedicalEntityExtractor extractor = new MedicalEntityExtractor();

    @Test
    void extractsLabeledDemographicFields() {
        String text = """
                Patient: John Kamau
                Doctor: Dr. Alice Wanjiru
                Facility: Nairobi Hospital
                Date: 2026-08-20
                Diagnosis: Suspected malaria
                Allergy: Penicillin
                """;

        List<ExtractedEntityDraft> entities = extractor.extract(text);

        assertThat(entities).extracting(ExtractedEntityDraft::entityType)
                .containsExactlyInAnyOrder(EntityType.PATIENT, EntityType.DOCTOR, EntityType.FACILITY,
                        EntityType.DATE, EntityType.DIAGNOSIS, EntityType.ALLERGY);

        assertThat(entities).filteredOn(e -> e.entityType() == EntityType.PATIENT)
                .extracting(ExtractedEntityDraft::label)
                .containsExactly("John Kamau");
    }

    @Test
    void extractsLabResultWithNumericValueUnitAndRange() {
        String text = "Hemoglobin: 13.4 g/dL (Reference: 12-16)";

        List<ExtractedEntityDraft> entities = extractor.extract(text);

        assertThat(entities).hasSize(1);
        ExtractedEntityDraft result = entities.get(0);
        assertThat(result.entityType()).isEqualTo(EntityType.LAB_RESULT);
        assertThat(result.label()).isEqualTo("Hemoglobin");
        assertThat(result.numericValue()).isEqualByComparingTo(new BigDecimal("13.4"));
        assertThat(result.unit()).isEqualTo("g/dL");
        assertThat(result.referenceRange()).isEqualTo("12-16");
        assertThat(result.status()).isNull(); // status not stated in text; EntityNormalizer derives it
    }

    @Test
    void extractsLabResultStatusWhenExplicitlyStated() {
        String text = "White Blood Cell Count: 11.2 x10^9/L (Reference: 4-11) HIGH";

        List<ExtractedEntityDraft> entities = extractor.extract(text);

        assertThat(entities).hasSize(1);
        assertThat(entities.get(0).status()).isEqualTo(ResultStatus.HIGH);
    }

    @Test
    void extractsMedicationNameAndDosage() {
        String text = "Amoxicillin 500mg - take three times daily for 7 days";

        List<ExtractedEntityDraft> entities = extractor.extract(text);

        assertThat(entities).hasSize(1);
        ExtractedEntityDraft medication = entities.get(0);
        assertThat(medication.entityType()).isEqualTo(EntityType.MEDICATION);
        assertThat(medication.label()).isEqualTo("Amoxicillin");
        assertThat(medication.value()).isEqualTo("500mg");
    }

    @Test
    void returnsEmptyListForBlankText() {
        assertThat(extractor.extract("")).isEmpty();
        assertThat(extractor.extract(null)).isEmpty();
    }

    @Test
    void ignoresLinesThatMatchNoKnownPattern() {
        String text = """
                This is a free-text sentence with no structure.
                Another unrelated line of narrative text.
                """;

        assertThat(extractor.extract(text)).isEmpty();
    }
}
