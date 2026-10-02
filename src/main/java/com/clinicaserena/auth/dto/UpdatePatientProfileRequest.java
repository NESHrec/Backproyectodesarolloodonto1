package com.clinicaserena.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePatientProfileRequest(
        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(min = 2, max = 160, message = "El nombre completo debe tener entre 2 y 160 caracteres")
        String fullName
) {
}
