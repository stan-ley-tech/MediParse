package com.mediparse.patient;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatientResponse create(@Valid @RequestBody CreatePatientRequest request) {
        return PatientResponse.from(patientService.create(request));
    }

    @GetMapping("/{id}")
    public PatientResponse getById(@PathVariable UUID id) {
        return PatientResponse.from(patientService.getById(id));
    }

    @GetMapping
    public Page<PatientResponse> search(@RequestParam(required = false) String query, Pageable pageable) {
        return patientService.search(query, pageable).map(PatientResponse::from);
    }
}
