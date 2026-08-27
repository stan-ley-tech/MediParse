package com.mediparse.patient;

import java.time.LocalDate;
import java.util.UUID;

public record PatientResponse(UUID id, String mrn, String fullName, LocalDate dateOfBirth, String sex) {

    public static PatientResponse from(Patient patient) {
        return new PatientResponse(patient.getId(), patient.getMrn(), patient.getFullName(),
                patient.getDateOfBirth(), patient.getSex());
    }
}
