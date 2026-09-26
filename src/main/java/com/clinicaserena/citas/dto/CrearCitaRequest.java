package com.clinicaserena.citas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** Petición del contrato; patientId se obtiene de la identidad autenticada, nunca de este DTO. */
public record CrearCitaRequest(
        @NotBlank String practitionerId,
        @NotBlank String specialtyId,
        @NotNull OffsetDateTime scheduledAt,
        @Size(max = 1000) String notes
) {
}
