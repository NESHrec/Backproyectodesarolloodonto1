package com.clinicaserena.auth.dto;
import jakarta.validation.constraints.*;
public record RegisterRequest(
        @NotBlank @Size(max=160) String nombre,
        @NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(min=8,max=72) String password) {}
