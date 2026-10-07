package com.clinicaserena.auditoria.dto;

import java.time.OffsetDateTime;

public record AuditEventResponse(
        String id,
        String actorAccountId,
        String actorType,
        String actorRole,
        String action,
        String entityType,
        String entityId,
        OffsetDateTime occurredAt
) {
}
