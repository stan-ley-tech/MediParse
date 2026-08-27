package com.mediparse.processing;

import com.mediparse.config.ProcessingProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Creates the processing_jobs row inside the caller's transaction, but only
 * actually publishes to RabbitMQ once that transaction has committed. Without
 * this split, a worker could pick up the message and query for a document
 * that — from its point of view — doesn't exist yet.
 */
@Component
public class ProcessingJobPublisher {

    private final ProcessingJobRepository processingJobRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final RabbitTemplate rabbitTemplate;
    private final ProcessingProperties processingProperties;

    public ProcessingJobPublisher(ProcessingJobRepository processingJobRepository,
                                   ApplicationEventPublisher applicationEventPublisher,
                                   RabbitTemplate rabbitTemplate,
                                   ProcessingProperties processingProperties) {
        this.processingJobRepository = processingJobRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.rabbitTemplate = rabbitTemplate;
        this.processingProperties = processingProperties;
    }

    public ProcessingJob enqueue(UUID documentId) {
        ProcessingJob job = processingJobRepository.save(
                new ProcessingJob(documentId, processingProperties.maxAttempts()));
        applicationEventPublisher.publishEvent(new JobCreatedEvent(job.getId(), documentId));
        return job;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onJobCreated(JobCreatedEvent event) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, RabbitMqConfig.ROUTING_KEY,
                new JobMessage(event.jobId(), event.documentId()));
    }
}
