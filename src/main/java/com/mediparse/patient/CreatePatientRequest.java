package com.mediparse.patient;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record CreatePatientRequest(
        @NotBlank String mrn,
        @NotBlank String fullName,
        LocalDate dateOfBirth,
        String sex
) {
}
