package com.mediparse.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Flat, denormalized view of a document written to OpenSearch. "content"
 * concatenates the extracted text with every extracted entity's label and
 * value so a single full-text query covers free-text terms like a test
 * name, a medication, or a patient's name without needing nested queries.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IndexedDocument {

    private String documentId;
    private String patientId;
    private String patientName;
    private String documentType;
    private String status;
    private String originalFilename;
    private String content;
    private Instant createdAt;
}
