package com.mediparse.extraction;

import com.mediparse.document.DocumentType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Classifies extracted text by keyword scoring: each document type has a set
 * of phrases that tend to appear in it, we count how many hits each type
 * gets, and the highest score wins. Simple, explainable, and easy to extend
 * with more phrases as real-world documents surface edge cases — a
 * reasonable starting point before reaching for a trained classifier.
 */
@Component
public class DocumentClassifier {

    private static final Map<DocumentType, List<String>> KEYWORDS = Map.of(
            DocumentType.LAB_REPORT, List.of(
                    "laboratory report", "lab report", "reference range", "specimen",
                    "test result", "hematology", "chemistry panel", "reference interval"),
            DocumentType.PRESCRIPTION, List.of(
                    "prescription", "rx", "dispense", "take three times daily", "take twice daily",
                    "medication", "dosage", "refill", "sig:"),
            DocumentType.DISCHARGE_SUMMARY, List.of(
                    "discharge summary", "admitted on", "date of admission", "date of discharge",
                    "hospital course", "discharge diagnosis", "discharge medications"),
            DocumentType.REFERRAL_LETTER, List.of(
                    "referral letter", "referring physician", "dear doctor", "kindly review",
                    "requesting your evaluation", "please see", "for further management")
    );

    public DocumentType classify(String text) {
        if (text == null || text.isBlank()) {
            return DocumentType.UNKNOWN;
        }
        String haystack = text.toLowerCase(Locale.ROOT);

        DocumentType best = DocumentType.UNKNOWN;
        int bestScore = 0;

        for (var entry : KEYWORDS.entrySet()) {
            int score = countHits(haystack, entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                best = entry.getKey();
            }
        }

        return best;
    }

    private int countHits(String haystack, List<String> phrases) {
        int score = 0;
        for (String phrase : phrases) {
            int fromIndex = 0;
            while ((fromIndex = haystack.indexOf(phrase, fromIndex)) != -1) {
                score++;
                fromIndex += phrase.length();
            }
        }
        return score;
    }
}
