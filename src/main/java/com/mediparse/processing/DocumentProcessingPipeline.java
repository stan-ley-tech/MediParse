package com.mediparse.processing;

import com.mediparse.audit.AuditAction;
import com.mediparse.audit.AuditLogService;
import com.mediparse.document.Document;
import com.mediparse.document.DocumentRepository;
import com.mediparse.document.DocumentStatus;
import com.mediparse.document.DocumentStorageService;
import com.mediparse.extraction.DocumentClassifier;
import com.mediparse.extraction.EntityNormalizer;
import com.mediparse.extraction.ExtractedEntity;
import com.mediparse.extraction.ExtractedEntityDraft;
import com.mediparse.extraction.ExtractedEntityRepository;
import com.mediparse.extraction.MedicalEntityExtractor;
import com.mediparse.patient.PatientRepository;
import com.mediparse.search.DocumentIndexer;
import com.mediparse.search.IndexedDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The seven pipeline stages between "file on disk" and "searchable" run here
 * in one database transaction per attempt: extract text, classify, extract
 * entities, normalize, persist, index, mark complete. Re-processing a
 * document (a retry, or a redelivered message) is safe because the entity
 * set for the document is replaced wholesale rather than appended to.
 */
@Service
public class DocumentProcessingPipeline {

    private final DocumentRepository documentRepository;
    private final PatientRepository patientRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final DocumentStorageService storageService;
    private final TextExtractionService textExtractionService;
    private final DocumentClassifier documentClassifier;
    private final MedicalEntityExtractor entityExtractor;
    private final EntityNormalizer entityNormalizer;
    private final ExtractedEntityRepository extractedEntityRepository;
    private final DocumentIndexer documentIndexer;
    private final AuditLogService auditLogService;

    public DocumentProcessingPipeline(DocumentRepository documentRepository,
                                       PatientRepository patientRepository,
                                       ProcessingJobRepository processingJobRepository,
                                       DocumentStorageService storageService,
                                       TextExtractionService textExtractionService,
                                       DocumentClassifier documentClassifier,
                                       MedicalEntityExtractor entityExtractor,
                                       EntityNormalizer entityNormalizer,
                                       ExtractedEntityRepository extractedEntityRepository,
                                       DocumentIndexer documentIndexer,
                                       AuditLogService auditLogService) {
        this.documentRepository = documentRepository;
        this.patientRepository = patientRepository;
        this.processingJobRepository = processingJobRepository;
        this.storageService = storageService;
        this.textExtractionService = textExtractionService;
        this.documentClassifier = documentClassifier;
        this.entityExtractor = entityExtractor;
        this.entityNormalizer = entityNormalizer;
        this.extractedEntityRepository = extractedEntityRepository;
        this.documentIndexer = documentIndexer;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void run(UUID jobId, UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Document " + documentId + " not found for processing job " + jobId));

        document.setStatus(DocumentStatus.PROCESSING);

        ExtractedText extracted = extractText(document);
        document.setExtractedTextCharCount(extracted.charCount());
        document.setDocumentType(documentClassifier.classify(extracted.content()));

        List<ExtractedEntity> entities = extractAndPersistEntities(document, extracted.content());
        String patientName = resolvePatientName(document.getPatientId());
        indexDocument(document, extracted, entities, patientName);

        document.setStatus(DocumentStatus.COMPLETED);
        document.setProcessingError(null);
        completeJob(jobId);

        auditLogService.recordSystem(AuditAction.PROCESSING_COMPLETED, "document", documentId.toString(),
                "classified as " + document.getDocumentType() + "; " + entities.size() + " entities extracted"
                        + (extracted.truncated() ? " (text truncated at extraction limit)" : ""));
    }

    private ExtractedText extractText(Document document) {
        try (InputStream in = storageService.load(document.getStoragePath())) {
            return textExtractionService.extract(in, document.getContentType());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read stored document for extraction", e);
        }
    }

    private List<ExtractedEntity> extractAndPersistEntities(Document document, String text) {
        // Wholesale replace so re-running this pipeline for the same document never duplicates rows.
        extractedEntityRepository.deleteAll(extractedEntityRepository.findByDocumentId(document.getId()));

        List<ExtractedEntityDraft> drafts = entityNormalizer.normalize(entityExtractor.extract(text));
        return drafts.stream()
                .map(draft -> extractedEntityRepository.save(new ExtractedEntity(
                        document.getId(), draft.entityType(), draft.label(), draft.value(),
                        draft.numericValue(), draft.unit(), draft.referenceRange(), draft.status(), null)))
                .collect(Collectors.toList());
    }

    private String resolvePatientName(UUID patientId) {
        if (patientId == null) {
            return null;
        }
        return patientRepository.findById(patientId).map(p -> p.getFullName()).orElse(null);
    }

    private void indexDocument(Document document, ExtractedText extracted, List<ExtractedEntity> entities,
                                String patientName) {
        String content = buildSearchableContent(extracted.content(), entities, patientName);
        IndexedDocument indexed = new IndexedDocument(
                document.getId().toString(),
                document.getPatientId() != null ? document.getPatientId().toString() : null,
                patientName,
                document.getDocumentType().name(),
                DocumentStatus.COMPLETED.name(),
                document.getOriginalFilename(),
                content,
                document.getCreatedAt()
        );
        try {
            documentIndexer.index(indexed);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to index document in OpenSearch", e);
        }
    }

    private String buildSearchableContent(String extractedText, List<ExtractedEntity> entities, String patientName) {
        StringBuilder sb = new StringBuilder(extractedText != null ? extractedText : "");
        if (patientName != null) {
            sb.append(' ').append(patientName);
        }
        for (ExtractedEntity entity : entities) {
            sb.append(' ').append(entity.getLabel());
            if (entity.getValue() != null) {
                sb.append(' ').append(entity.getValue());
            }
        }
        return sb.toString();
    }

    private void completeJob(UUID jobId) {
        ProcessingJob job = processingJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Processing job " + jobId + " not found"));
        job.setStatus(ProcessingJobStatus.COMPLETED);
        job.setCompletedAt(Instant.now());
    }
}
