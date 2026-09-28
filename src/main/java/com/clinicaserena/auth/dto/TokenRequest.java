package com.clinicaserena.auth.dto;
import jakarta.validation.constraints.*;
public record TokenRequest(@NotBlank @Size(max=200) String token) {}
