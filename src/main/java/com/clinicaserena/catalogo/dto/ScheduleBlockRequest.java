package com.clinicaserena.catalogo.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record ScheduleBlockRequest(
        @NotNull(message = "El inicio es obligatorio") OffsetDateTime startAt,
        @NotNull(message = "El fin es obligatorio") OffsetDateTime endAt
) {
}
