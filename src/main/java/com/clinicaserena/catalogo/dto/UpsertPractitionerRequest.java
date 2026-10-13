package com.clinicaserena.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertPractitionerRequest(
        @NotBlank @Size(max = 160) String fullName,
        @NotBlank @Size(max = 36) String specialtyId,
        @NotBlank @Size(max = 50) String licenseNumber
) {}
