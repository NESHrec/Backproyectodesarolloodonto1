package com.clinicaserena.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 254, message = "El correo no puede exceder 254 caracteres")
        String email,
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 128, message = "La contraseña no puede exceder 128 caracteres")
        String password
) {
}
