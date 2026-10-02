package com.clinicaserena.odontograma.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecordDentalObservationRequest(
        @Min(11) @Max(48) int toothNumber,
        @NotBlank @Size(max = 500) String observation
) {
}
