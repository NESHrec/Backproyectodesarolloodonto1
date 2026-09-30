package com.clinicaserena.pagos.dto;

import com.clinicaserena.citas.entity.EstadoCita;

import java.time.OffsetDateTime;
import java.util.List;

public record BillingAppointmentResponse(
        String id,
        String patientId,
        String practitionerId,
        String specialtyId,
        OffsetDateTime scheduledAt,
        EstadoCita status,
        boolean attended,
        Long chargeAmount,
        String currency,
        long paidAmount,
        Long balanceAmount,
        boolean chargeDefined,
        List<PaymentResponse> payments
) {
}
