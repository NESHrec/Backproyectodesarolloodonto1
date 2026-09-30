package com.clinicaserena.pagos.dto;

import com.clinicaserena.pagos.entity.MetodoPago;

import java.time.OffsetDateTime;

public record PaymentResponse(
        String id,
        String appointmentId,
        String registeredByAccountId,
        Long amount,
        String currency,
        MetodoPago method,
        String reference,
        String idempotencyKey,
        OffsetDateTime registeredAt
) {
}
