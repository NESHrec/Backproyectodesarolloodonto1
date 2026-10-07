package com.clinicaserena.recepcion.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
public record ConfirmPatientLinkRequest(@NotBlank String requestId,
                                        @NotNull(message="El codigo es obligatorio")
                                        @Pattern(regexp="[0-9]{8}", message="El codigo debe tener exactamente ocho digitos") String code) {}
