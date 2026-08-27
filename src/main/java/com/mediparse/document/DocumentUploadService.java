package com.mediparse.document;

import com.mediparse.audit.AuditAction;
import com.mediparse.audit.AuditLogService;
import com.mediparse.common.BadRequestException;
import com.mediparse.common.NotFoundException;
import com.mediparse.processing.ProcessingJobPublisher;
import com.mediparse.processing.ProcessingJobRepository;
import com.mediparse.search.DocumentIndexer;
import com.mediparse.security.CurrentUserService;
import com.mediparse.user.Role;
import com.mediparse.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentUploadService {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadService.class);

    private final DocumentRepository documentRepository;
    private final DocumentStorageService storageService;
    private final FileValidationService fileValidationService;
    private final ProcessingJobPublisher processingJobPublisher;
    private final AuditLogService auditLogService;
    private final CurrentUserService currentUserService;
    private final DocumentAccessService documentAccessService;
    private final DocumentIndexer documentIndexer;
    private final ProcessingJobRepository processingJobRepository;

    public DocumentUploadService(DocumentRepository documentRepository,
                                  DocumentStorageService storageService,
                                  FileValidationService fileValidationService,
                                  ProcessingJobPublisher processingJobPublisher,
                                  AuditLogService auditLogService,
                                  CurrentUserService currentUserService,
                                  DocumentAccessService documentAccessService,
                                  DocumentIndexer documentIndexer,
                                  ProcessingJobRepository processingJobRepository) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.fileValidationService = fileValidationService;
        this.processingJobPublisher = processingJobPublisher;
        this.auditLogService = auditLogService;
        this.currentUserService = currentUserService;
        this.documentAccessService = documentAccessService;
        this.documentIndexer = documentIndexer;
        this.processingJobRepository = processingJobRepository;
    }

    @Transactional
    public Document upload(MultipartFile file, UUID patientId) {
        return upload(file, patientId, null);
    }

    @Transactional
    public Document uploadNewVersion(UUID parentDocumentId, MultipartFile file) {
        Document parent = getById(parentDocumentId);
        return upload(file, parent.getPatientId(), parent);
    }

    private Document upload(MultipartFile file, UUID patientId, Document previousVersion) {
        var actor = currentUserService.require();
        String originalFilename = file.getOriginalFilename();

        fileValidationService.validateMetadata(originalFilename, file.getSize());
        sniffContentOrReject(file, originalFilename);

        StoredFile stored = storeFile(file, originalFilename);

        Optional<Document> duplicate = documentRepository.findByUploadedByAndFileHash(actor.getId(), stored.sha256Hash());
        if (duplicate.isPresent()) {
            deleteQuietly(stored.relativePath());
            auditLogService.record(actor.getId(), AuditAction.UPLOAD, "document",
                    duplicate.get().getId().toString(), "duplicate submission — returned existing document");
            return duplicate.get();
        }

        Document document = new Document(patientId, actor.getId(), originalFilename, file.getContentType(),
                stored.sizeBytes(), stored.sha256Hash(), stored.relativePath(), previousVersion);
        document = documentRepository.save(document);

        document.setStatus(DocumentStatus.QUEUED);
        processingJobPublisher.enqueue(document.getId());

        auditLogService.record(actor.getId(), AuditAction.UPLOAD, "document", document.getId().toString(), originalFilename);
        return document;
    }

    public Document getById(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Document " + id + " not found"));
    }

    public Document getForViewing(UUID id) {
        Document document = getById(id);
        documentAccessService.checkCanView(currentUserService.require(), document);
        auditLogService.record(currentUserService.require().getId(), AuditAction.VIEW, "document", id.toString(), null);
        return document;
    }

    public Page<Document> listByPatient(UUID patientId, Pageable pageable) {
        User actor = currentUserService.require();
        if (actor.getRole() == Role.STAFF) {
            return documentRepository.findByPatientIdAndUploadedBy(patientId, actor.getId(), pageable);
        }
        return documentRepository.findByPatientId(patientId, pageable);
    }

    public List<Document> getVersionHistory(UUID id) {
        Document document = getById(id);
        documentAccessService.checkCanView(currentUserService.require(), document);
        return documentRepository.findByVersionGroupIdOrderByVersionNumberDesc(document.getVersionGroupId());
    }

    @Transactional
    public void delete(UUID id) {
        Document document = getById(id);
        var actor = currentUserService.require();
        documentAccessService.checkCanDelete(actor, document);

        deleteQuietly(document.getStoragePath());
        documentIndexer.delete(document.getId());
        processingJobRepository.deleteByDocumentId(document.getId());
        documentRepository.delete(document);
        auditLogService.record(actor.getId(), AuditAction.DELETE, "document", id.toString(), document.getOriginalFilename());
    }

    private void sniffContentOrReject(MultipartFile file, String originalFilename) {
        try (InputStream in = file.getInputStream()) {
            fileValidationService.validateContent(in, originalFilename);
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file");
        }
    }

    private StoredFile storeFile(MultipartFile file, String originalFilename) {
        try (InputStream in = file.getInputStream()) {
            return storageService.store(in, originalFilename);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded file", e);
        }
    }

    private void deleteQuietly(String relativePath) {
        try {
            storageService.delete(relativePath);
        } catch (IOException e) {
            log.warn("Failed to delete stored file at {}", relativePath, e);
        }
    }
}
