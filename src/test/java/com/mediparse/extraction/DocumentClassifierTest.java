package com.mediparse.extraction;

import com.mediparse.document.DocumentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentClassifierTest {

    private final DocumentClassifier classifier = new DocumentClassifier();

    @Test
    void classifiesLabReportByReferenceRangeLanguage() {
        String text = """
                LABORATORY REPORT
                Specimen: Blood
                Hemoglobin: 13.4 g/dL (Reference range: 12-16)
                """;

        assertThat(classifier.classify(text)).isEqualTo(DocumentType.LAB_REPORT);
    }

    @Test
    void classifiesPrescriptionByDosageLanguage() {
        String text = """
                PRESCRIPTION
                Amoxicillin 500mg - take three times daily
                Dosage instructions attached. Refill: 1
                """;

        assertThat(classifier.classify(text)).isEqualTo(DocumentType.PRESCRIPTION);
    }

    @Test
    void classifiesDischargeSummaryByAdmissionLanguage() {
        String text = """
                DISCHARGE SUMMARY
                Date of admission: 2026-07-25
                Hospital course was unremarkable.
                Discharge diagnosis: pneumonia
                """;

        assertThat(classifier.classify(text)).isEqualTo(DocumentType.DISCHARGE_SUMMARY);
    }

    @Test
    void classifiesReferralLetterByReferralLanguage() {
        String text = """
                Dear Doctor,
                I am writing this referral letter as the referring physician
                requesting your evaluation of this patient.
                """;

        assertThat(classifier.classify(text)).isEqualTo(DocumentType.REFERRAL_LETTER);
    }

    @Test
    void returnsUnknownWhenTextHasNoRecognizableKeywords() {
        assertThat(classifier.classify("Just some unrelated text with no medical keywords."))
                .isEqualTo(DocumentType.UNKNOWN);
    }

    @Test
    void returnsUnknownForBlankText() {
        assertThat(classifier.classify("   ")).isEqualTo(DocumentType.UNKNOWN);
    }
}
