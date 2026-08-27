package com.mediparse.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByUploadedByAndFileHash(UUID uploadedBy, String fileHash);

    List<Document> findByVersionGroupIdOrderByVersionNumberDesc(UUID versionGroupId);

    Page<Document> findByUploadedBy(UUID uploadedBy, Pageable pageable);

    Page<Document> findByPatientId(UUID patientId, Pageable pageable);
}
