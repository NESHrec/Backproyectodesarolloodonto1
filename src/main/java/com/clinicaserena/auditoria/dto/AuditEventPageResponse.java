package com.clinicaserena.auditoria.dto;

import java.util.List;

public record AuditEventPageResponse(
        List<AuditEventResponse> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
}
