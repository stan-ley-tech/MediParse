package com.mediparse.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listens on the document-processing queue with several concurrent consumer
 * threads (see mediparse.rabbitmq.listener.simple.concurrency). Claiming the
 * job via {@link ProcessingJobRepository#claim} before doing any work is what
 * keeps two threads — or a message redelivered after a crash mid-processing —
 * from processing the same document twice.
 */
@Component
public class DocumentProcessingWorker {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingWorker.class);

    private final ProcessingJobRepository processingJobRepository;
    private final DocumentProcessingService documentProcessingService;

    public DocumentProcessingWorker(ProcessingJobRepository processingJobRepository,
                                     DocumentProcessingService documentProcessingService) {
        this.processingJobRepository = processingJobRepository;
        this.documentProcessingService = documentProcessingService;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE)
    public void handle(JobMessage message) {
        int claimed = processingJobRepository.claim(message.jobId());
        if (claimed == 0) {
            log.debug("Job {} was already claimed or finished; ignoring redelivered message", message.jobId());
            return;
        }

        try {
            documentProcessingService.process(message.jobId(), message.documentId());
        } catch (Exception e) {
            log.error("Processing permanently failed for document {} (job {})",
                    message.documentId(), message.jobId(), e);
            documentProcessingService.markFailed(message.jobId(), message.documentId(), e);
            // Rethrow so the broker dead-letters the message instead of silently dropping it.
            throw e;
        }
    }
}
