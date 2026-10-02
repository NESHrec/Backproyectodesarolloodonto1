package com.clinicaserena.recepcion.dto;

import java.util.List;

public record AdministrativePatientPageResponse(
        List<AdministrativePatientResponse> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
}
