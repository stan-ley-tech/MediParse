package com.mediparse.processing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {

    /**
     * Atomically transitions a job from PENDING to IN_PROGRESS. Returns the
     * number of rows updated (0 or 1) so the caller can tell whether it won
     * the race to process this job — this is what keeps two consumer threads,
     * or a redelivered message after a crash, from processing the same job twice.
     */
    @Modifying
    @Transactional
    @Query("update ProcessingJob j set j.status = com.mediparse.processing.ProcessingJobStatus.IN_PROGRESS, " +
            "j.startedAt = CURRENT_TIMESTAMP, j.attempts = j.attempts + 1 " +
            "where j.id = :id and j.status = com.mediparse.processing.ProcessingJobStatus.PENDING")
    int claim(@Param("id") UUID id);
}
