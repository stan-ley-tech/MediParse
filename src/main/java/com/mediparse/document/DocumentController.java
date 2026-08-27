package com.mediparse.document;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentUploadService documentUploadService;
    private final SignedUrlService signedUrlService;

    public DocumentController(DocumentUploadService documentUploadService,
                               SignedUrlService signedUrlService) {
        this.documentUploadService = documentUploadService;
        this.signedUrlService = signedUrlService;
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(required = false) UUID patientId) {
        return DocumentResponse.from(documentUploadService.upload(file, patientId));
    }

    @PostMapping(path = "/{id}/versions", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse uploadNewVersion(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return DocumentResponse.from(documentUploadService.uploadNewVersion(id, file));
    }

    @GetMapping("/{id}")
    public DocumentResponse getById(@PathVariable UUID id) {
        return DocumentResponse.from(documentUploadService.getForViewing(id));
    }

    @GetMapping("/{id}/versions")
    public List<DocumentResponse> getVersionHistory(@PathVariable UUID id) {
        return documentUploadService.getVersionHistory(id).stream().map(DocumentResponse::from).toList();
    }

    @GetMapping
    public Page<DocumentResponse> byPatient(@RequestParam UUID patientId, Pageable pageable) {
        return documentUploadService.listByPatient(patientId, pageable).map(DocumentResponse::from);
    }

    @PostMapping("/{id}/download-url")
    public DownloadUrlResponse createDownloadUrl(@PathVariable UUID id, HttpServletRequest request) {
        documentUploadService.getForViewing(id);
        SignedDownload signed = signedUrlService.sign(id);

        String url = UriComponentsBuilder.fromHttpUrl(request.getRequestURL().toString())
                .replacePath("/api/v1/downloads/" + id)
                .replaceQuery(null)
                .queryParam("expires", signed.expiresAt())
                .queryParam("signature", signed.signature())
                .toUriString();

        return new DownloadUrlResponse(url, Instant.ofEpochSecond(signed.expiresAt()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        documentUploadService.delete(id);
    }
}
