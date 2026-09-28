package com.clinicaserena.auth.dto;
import jakarta.validation.constraints.*;
public record ResetPasswordRequest(@NotBlank @Size(max=200) String token,
        @NotBlank @Size(min=8,max=72) String password) {}
