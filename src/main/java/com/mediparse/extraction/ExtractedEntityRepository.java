package com.mediparse.extraction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExtractedEntityRepository extends JpaRepository<ExtractedEntity, UUID> {

    List<ExtractedEntity> findByDocumentId(UUID documentId);
}
