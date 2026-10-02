package com.clinicaserena.odontograma.dto;

import java.util.List;

public record DentalObservationPageResponse(
        String patientId,
        String patientName,
        List<DentalObservationResponse> observations
) {
}
