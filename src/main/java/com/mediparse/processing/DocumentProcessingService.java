package com.mediparse.processing;

import com.mediparse.audit.AuditAction;
import com.mediparse.audit.AuditLogService;
import com.mediparse.document.DocumentRepository;
import com.mediparse.document.DocumentStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Retries transient failures (a momentary OpenSearch blip, a lock timeout)
 * in-process with backoff before giving up. Retry lives on this bean and the
 * actual work lives on {@link DocumentProcessingPipeline} in a different
 * bean deliberately: Spring's retry and transaction proxies both only take
 * effect on calls that go through the bean proxy, so if the two concerns
 * shared one method a self-invocation would silently skip one of them.
 */
@Service
public class DocumentProcessingService {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final DocumentProcessingPipeline pipeline;
    private final DocumentRepository documentRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final AuditLogService auditLogService;

    public DocumentProcessingService(DocumentProcessingPipeline pipeline,
                                      DocumentRepository documentRepository,
                                      ProcessingJobRepository processingJobRepository,
                                      AuditLogService auditLogService) {
        this.pipeline = pipeline;
        this.documentRepository = documentRepository;
        this.processingJobRepository = processingJobRepository;
        this.auditLogService = auditLogService;
    }

    @Retryable(retryFor = Exception.class, maxAttemptsExpression = "${mediparse.processing.max-attempts:3}",
            backoff = @Backoff(delay = 500, multiplier = 2))
    public void process(UUID jobId, UUID documentId) {
        pipeline.run(jobId, documentId);
    }

    @Transactional
    public void markFailed(UUID jobId, UUID documentId, Exception cause) {
        String message = truncate(cause.getMessage() != null ? cause.getMessage() : cause.toString());

        documentRepository.findById(documentId).ifPresent(document -> {
            document.setStatus(DocumentStatus.FAILED);
            document.setProcessingError(message);
        });

        processingJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(ProcessingJobStatus.FAILED);
            job.setLastError(message);
            job.setCompletedAt(Instant.now());
        });

        auditLogService.recordSystem(AuditAction.PROCESSING_FAILED, "document", documentId.toString(), message);
    }

    private String truncate(String message) {
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
