package com.clinicaserena.pagos.dto;

import com.clinicaserena.pagos.entity.MetodoPago;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegisterPaymentRequest(
        @NotNull @Positive Long amount,
        @NotNull MetodoPago method,
        @Size(max = 120) String reference,
        @NotBlank @Size(max = 80) String idempotencyKey
) {
}
