package com.mediparse.patient;

import com.mediparse.common.ConflictException;
import com.mediparse.common.NotFoundException;
import com.mediparse.security.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final CurrentUserService currentUserService;

    public PatientService(PatientRepository patientRepository, CurrentUserService currentUserService) {
        this.patientRepository = patientRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public Patient create(CreatePatientRequest request) {
        if (patientRepository.existsByMrn(request.mrn())) {
            throw new ConflictException("A patient with MRN " + request.mrn() + " already exists");
        }
        var actor = currentUserService.require();
        Patient patient = new Patient(request.mrn(), request.fullName(), request.dateOfBirth(),
                request.sex(), actor.getId());
        return patientRepository.save(patient);
    }

    public Patient getById(UUID id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Patient " + id + " not found"));
    }

    public Page<Patient> search(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return patientRepository.findAll(pageable);
        }
        return patientRepository.findByFullNameContainingIgnoreCase(query, pageable);
    }
}
