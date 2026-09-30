package com.clinicaserena.pagos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record SetAppointmentChargeRequest(
        @NotNull @Positive Long amount,
        @Pattern(regexp = "GTQ") String currency
) {
}
