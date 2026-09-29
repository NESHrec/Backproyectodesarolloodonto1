package com.clinicaserena.staff.dto;

import com.clinicaserena.staff.entity.RolPersonal;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateStaffAccountRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(max = 160) String fullName,
        @NotNull RolPersonal role,
        @NotBlank @Size(min = 12, max = 72) String password
) {
}
