package com.clinicaserena.pagos.dto;

import com.clinicaserena.pagos.entity.EstadoIntencionPago;
import com.clinicaserena.pagos.entity.MetodoPago;

import java.time.OffsetDateTime;

public record PaymentIntentResponse(
        String appointmentId,
        Long amount,
        MetodoPago method,
        String reference,
        String idempotencyKey,
        EstadoIntencionPago status,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
}
