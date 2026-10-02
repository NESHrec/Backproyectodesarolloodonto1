package com.clinicaserena.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertSpecialtyRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String name,
        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
        String description
) {
}
