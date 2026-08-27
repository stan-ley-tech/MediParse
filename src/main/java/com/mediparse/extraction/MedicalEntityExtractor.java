package com.mediparse.extraction;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls structured facts out of extracted document text using targeted,
 * line-oriented patterns rather than a general NLP model — every medical
 * document this system ingests is expected to follow a "Label: value" or
 * tabular lab-result convention, so a handful of well-scoped regexes cover
 * the real cases cheaply and predictably. Each line is matched against the
 * patterns in order of specificity so a lab-result line never gets
 * mis-parsed as a generic labeled field.
 */
@Component
public class MedicalEntityExtractor {

    private static final Pattern LABELED_FIELD = Pattern.compile(
            "^(Patient|Doctor|Facility|Date|Diagnosis|Allerg(?:y|ies)):\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LAB_RESULT = Pattern.compile(
            "^([A-Za-z][A-Za-z0-9 /\\-]*?):\\s*([\\d.]+)\\s*([A-Za-z%/^0-9]*)\\s*" +
                    "\\(Reference:\\s*([\\d.]+)\\s*-\\s*([\\d.]+)\\)\\s*(NORMAL|HIGH|LOW|ABNORMAL)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MEDICATION_LINE = Pattern.compile(
            "^([A-Z][A-Za-z]+(?:\\s[A-Z][a-zA-Z]+)?)\\s+(\\d+(?:\\.\\d+)?\\s?(?:mg|mcg|g|ml|IU))\\b");

    public List<ExtractedEntityDraft> extract(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<ExtractedEntityDraft> results = new ArrayList<>();
        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            parseLine(line).ifPresent(results::add);
        }
        return results;
    }

    private Optional<ExtractedEntityDraft> parseLine(String line) {
        Matcher labResult = LAB_RESULT.matcher(line);
        if (labResult.find()) {
            return Optional.of(toLabResult(labResult));
        }

        Matcher labeledField = LABELED_FIELD.matcher(line);
        if (labeledField.find()) {
            return Optional.of(toLabeledField(labeledField));
        }

        Matcher medication = MEDICATION_LINE.matcher(line);
        if (medication.find()) {
            return Optional.of(toMedication(medication));
        }

        return Optional.empty();
    }

    private ExtractedEntityDraft toLabResult(Matcher m) {
        String testName = m.group(1).strip();
        String rawValue = m.group(2).strip();
        String unit = emptyToNull(m.group(3));
        String referenceRange = m.group(4).strip() + "-" + m.group(5).strip();
        ResultStatus status = m.group(6) != null ? ResultStatus.valueOf(m.group(6).toUpperCase(Locale.ROOT)) : null;

        return new ExtractedEntityDraft(EntityType.LAB_RESULT, testName, rawValue,
                parseDecimal(rawValue), unit, referenceRange, status);
    }

    private ExtractedEntityDraft toLabeledField(Matcher m) {
        String key = m.group(1).toLowerCase(Locale.ROOT);
        String value = m.group(2).strip();
        EntityType type = switch (key) {
            case "patient" -> EntityType.PATIENT;
            case "doctor" -> EntityType.DOCTOR;
            case "facility" -> EntityType.FACILITY;
            case "date" -> EntityType.DATE;
            case "diagnosis" -> EntityType.DIAGNOSIS;
            default -> EntityType.ALLERGY; // "allergy" / "allergies"
        };
        return new ExtractedEntityDraft(type, value, null, null, null, null, null);
    }

    private ExtractedEntityDraft toMedication(Matcher m) {
        String name = m.group(1).strip();
        String dosage = m.group(2).strip();
        return new ExtractedEntityDraft(EntityType.MEDICATION, name, dosage, null, null, null, null);
    }

    private BigDecimal parseDecimal(String raw) {
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }
}
