package com.clinicaserena.catalogo.dto;

import java.time.OffsetDateTime;

public record AvailabilitySlotDto(
        String id,
        String practitionerId,
        OffsetDateTime startAt,
        OffsetDateTime endAt
) {
}
