package com.mediparse.document;

import com.mediparse.audit.AuditLogService;
import com.mediparse.processing.ProcessingJobPublisher;
import com.mediparse.processing.ProcessingJobRepository;
import com.mediparse.search.DocumentIndexer;
import com.mediparse.security.CurrentUserService;
import com.mediparse.user.Role;
import com.mediparse.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Exercises the two branches that are awkward to set up through a real
 * upload (a byte-identical duplicate, and everything a deletion needs to
 * clean up) against mocked collaborators, rather than standing up Postgres,
 * RabbitMQ and OpenSearch just to prove these decisions are made correctly.
 */
@ExtendWith(MockitoExtension.class)
class DocumentUploadServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentStorageService storageService;
    @Mock
    private FileValidationService fileValidationService;
    @Mock
    private ProcessingJobPublisher processingJobPublisher;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private DocumentAccessService documentAccessService;
    @Mock
    private DocumentIndexer documentIndexer;
    @Mock
    private ProcessingJobRepository processingJobRepository;

    @InjectMocks
    private DocumentUploadService documentUploadService;

    @Test
    void resubmittingAByteIdenticalFileReturnsTheExistingDocumentWithoutQueueingNewWork() throws IOException {
        User uploader = user(Role.CLINICIAN);
        when(currentUserService.require()).thenReturn(uploader);

        var file = new MockMultipartFile("file", "lab.pdf", "application/pdf", "content".getBytes());
        when(storageService.store(any(), eq("lab.pdf")))
                .thenReturn(new StoredFile("2026/08/new-copy.pdf", 7, "same-hash"));

        Document existing = new Document(null, uploader.getId(), "lab.pdf", "application/pdf", 7, "same-hash",
                "2026/08/original.pdf", null);
        when(documentRepository.findByUploadedByAndFileHash(uploader.getId(), "same-hash"))
                .thenReturn(Optional.of(existing));

        Document result = documentUploadService.upload(file, null);

        assertThat(result).isSameAs(existing);
        verify(storageService).delete("2026/08/new-copy.pdf");
        verify(documentRepository, never()).save(any());
        verify(processingJobPublisher, never()).enqueue(any());
    }

    @Test
    void deletingADocumentCleansUpStorageIndexAndJobsBeforeRemovingTheRow() throws IOException {
        UUID documentId = UUID.randomUUID();
        User admin = user(Role.ADMIN);
        Document document = new Document(null, UUID.randomUUID(), "lab.pdf", "application/pdf", 7,
                "hash", "2026/08/target.pdf", null);

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(currentUserService.require()).thenReturn(admin);

        documentUploadService.delete(documentId);

        verify(documentAccessService).checkCanDelete(admin, document);
        verify(storageService).delete("2026/08/target.pdf");
        verify(documentIndexer).delete(document.getId());
        verify(processingJobRepository).deleteByDocumentId(document.getId());
        verify(documentRepository).delete(document);
    }

    private User user(Role role) {
        User user = new User("user@example.com", "hashed", "Test User", role);
        user.setId(UUID.randomUUID());
        return user;
    }
}
