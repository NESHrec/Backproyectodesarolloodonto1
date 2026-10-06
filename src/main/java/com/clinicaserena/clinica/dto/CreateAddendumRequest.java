package com.clinicaserena.clinica.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size;
public record CreateAddendumRequest(@NotBlank @Size(min=3,max=2000) String text,
                                    @NotBlank @Size(min=3,max=500) String reason) {}
