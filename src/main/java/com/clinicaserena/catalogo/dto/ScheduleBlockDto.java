package com.clinicaserena.catalogo.dto;

import java.time.OffsetDateTime;

public record ScheduleBlockDto(
        String id,
        String practitionerId,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        boolean available
) {
}
