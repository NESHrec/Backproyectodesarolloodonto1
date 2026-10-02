package com.clinicaserena.recepcion.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAdministrativePatientRequest(
        @NotBlank @Size(min = 3, max = 160) String fullName,
        @NotBlank @Size(min = 7, max = 40) String phone,
        @Email @Size(max = 254) String email
) {
}
