package com.mediparse.document;

import com.mediparse.audit.AuditAction;
import com.mediparse.audit.AuditLogService;
import com.mediparse.common.ForbiddenException;
import com.mediparse.common.NotFoundException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * Deliberately outside Spring Security's authenticated zone: access here is
 * governed entirely by possessing a valid, unexpired signature rather than a
 * bearer token, which is what makes the link usable from things like a PDF
 * viewer or an email without embedding credentials in a URL.
 */
@RestController
@RequestMapping("/api/v1/downloads")
public class DownloadController {

    private final DocumentRepository documentRepository;
    private final DocumentStorageService storageService;
    private final SignedUrlService signedUrlService;
    private final AuditLogService auditLogService;

    public DownloadController(DocumentRepository documentRepository,
                               DocumentStorageService storageService,
                               SignedUrlService signedUrlService,
                               AuditLogService auditLogService) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.signedUrlService = signedUrlService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id,
                                                          @RequestParam long expires,
                                                          @RequestParam String signature) {
        if (!signedUrlService.isValid(id, expires, signature)) {
            throw new ForbiddenException("Download link is invalid or has expired");
        }

        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Document " + id + " not found"));

        InputStreamResource resource;
        try {
            resource = new InputStreamResource(storageService.load(document.getStoragePath()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read stored document", e);
        }

        auditLogService.recordSystem(AuditAction.DOWNLOAD, "document", id.toString(), "downloaded via signed URL");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.getOriginalFilename()).build().toString())
                .body(resource);
    }
}
