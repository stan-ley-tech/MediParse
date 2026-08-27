package com.mediparse.processing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * The worker's own logic — claim, delegate, and on failure mark-then-rethrow
 * — is a handful of branches that are much faster to verify against a
 * mocked {@link DocumentProcessingService} than through an actual queue.
 */
@ExtendWith(MockitoExtension.class)
class DocumentProcessingWorkerTest {

    @Mock
    private ProcessingJobRepository processingJobRepository;
    @Mock
    private DocumentProcessingService documentProcessingService;

    @InjectMocks
    private DocumentProcessingWorker worker;

    @Test
    void skipsProcessingWhenTheJobCouldNotBeClaimed() {
        UUID jobId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(processingJobRepository.claim(jobId)).thenReturn(0);

        worker.handle(new JobMessage(jobId, documentId));

        verify(documentProcessingService, never()).process(any(), any());
        verify(documentProcessingService, never()).markFailed(any(), any(), any());
    }

    @Test
    void processesTheJobOnceItIsSuccessfullyClaimed() {
        UUID jobId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(processingJobRepository.claim(jobId)).thenReturn(1);

        worker.handle(new JobMessage(jobId, documentId));

        verify(documentProcessingService).process(jobId, documentId);
        verify(documentProcessingService, never()).markFailed(any(), any(), any());
    }

    @Test
    void marksTheJobFailedAndRethrowsWhenProcessingExhaustsItsRetries() {
        UUID jobId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(processingJobRepository.claim(jobId)).thenReturn(1);
        RuntimeException failure = new RuntimeException("OpenSearch unavailable");
        doThrow(failure).when(documentProcessingService).process(jobId, documentId);

        assertThatThrownBy(() -> worker.handle(new JobMessage(jobId, documentId)))
                .isSameAs(failure);

        verify(documentProcessingService).markFailed(jobId, documentId, failure);
    }
}
