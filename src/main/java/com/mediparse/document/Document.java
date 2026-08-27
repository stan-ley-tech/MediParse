package com.mediparse.document;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document {

    @Id
    private UUID id;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "original_filename", nullable = false, length = 512)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "storage_path", nullable = false, length = 1024)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32)
    private DocumentType documentType = DocumentType.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentStatus status = DocumentStatus.UPLOADED;

    @Column(name = "version_number", nullable = false)
    private int versionNumber = 1;

    @Column(name = "parent_document_id")
    private UUID parentDocumentId;

    /** Shared by every version of the same logical document; equals the id of the first version. */
    @Column(name = "version_group_id", nullable = false)
    private UUID versionGroupId;

    @Column(name = "processing_error", columnDefinition = "text")
    private String processingError;

    @Column(name = "processing_attempts", nullable = false)
    private int processingAttempts = 0;

    @Column(name = "extracted_text_char_count")
    private Integer extractedTextCharCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Document(UUID patientId, UUID uploadedBy, String originalFilename, String contentType,
                     long fileSizeBytes, String fileHash, String storagePath,
                     Document previousVersion) {
        this.id = UUID.randomUUID();
        this.patientId = patientId;
        this.uploadedBy = uploadedBy;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.fileHash = fileHash;
        this.storagePath = storagePath;

        if (previousVersion != null) {
            this.parentDocumentId = previousVersion.getId();
            this.versionGroupId = previousVersion.getVersionGroupId();
            this.versionNumber = previousVersion.getVersionNumber() + 1;
        } else {
            this.versionGroupId = this.id;
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isOwnedBy(UUID userId) {
        return uploadedBy.equals(userId);
    }
}
