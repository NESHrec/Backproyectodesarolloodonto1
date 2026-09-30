package com.clinicaserena.clinica.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Datos clínicos mínimos de una atención. Cita, paciente y profesional nunca se
 * reciben aquí: se derivan de la ruta y del principal autenticado.
 */
public record RecordAttentionRequest(
        @NotBlank @Size(min = 3, max = 1000) String reason,
        @Size(max = 2000) String findings,
        @NotBlank @Size(min = 3, max = 1000) String diagnosis,
        @Size(max = 2000) String treatmentPlan,
        @NotNull @Size(max = 10) List<@NotNull @Valid PrescriptionItemRequest> prescription
) {

    public record PrescriptionItemRequest(
            @NotBlank @Size(min = 2, max = 160) String medicine,
            @NotBlank @Size(max = 80) String dose,
            @NotBlank @Size(max = 80) String frequency,
            @NotBlank @Size(max = 80) String duration,
            @Size(max = 500) String instructions
    ) {
    }
}
