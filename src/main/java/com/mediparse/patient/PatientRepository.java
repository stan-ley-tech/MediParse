package com.mediparse.patient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByMrn(String mrn);

    boolean existsByMrn(String mrn);

    Page<Patient> findByFullNameContainingIgnoreCase(String fullName, Pageable pageable);
}
