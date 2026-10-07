package com.clinicaserena.auditoria.service;

import com.clinicaserena.auditoria.dto.AuditEventPageResponse;
import com.clinicaserena.auditoria.dto.AuditEventResponse;
import com.clinicaserena.auditoria.entity.BitacoraEvento;
import com.clinicaserena.auditoria.repository.BitacoraEventoRepository;
import com.clinicaserena.common.exception.ApiException;
import com.clinicaserena.staff.entity.RolPersonal;
import com.clinicaserena.staff.security.StaffPrincipal;
import com.clinicaserena.auth.security.PatientPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class BitacoraService {

    private static final int MAX_LIMIT = 100;
    private final BitacoraEventoRepository repository;

    public BitacoraService(BitacoraEventoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String actorAccountId, String action, String entityType, String entityId,
                       OffsetDateTime occurredAt) {
        throw new IllegalStateException("Use record(StaffPrincipal, ...) so the actor role is audited");
    }

    @Transactional
    public void record(StaffPrincipal actor, String action, String entityType, String entityId,
                       OffsetDateTime occurredAt) {
        repository.saveAndFlush(BitacoraEvento.registrar(UUID.randomUUID().toString(), actor.accountId(), "PERSONAL",
                actor.accountId(), actor.role().name(), action,
                entityType, entityId, occurredAt));
    }

    @Transactional
    public void recordPatient(PatientPrincipal actor, String action, String entityType, String entityId,
                              OffsetDateTime occurredAt) {
        repository.saveAndFlush(BitacoraEvento.registrar(UUID.randomUUID().toString(), null, "PACIENTE",
                actor.accountId(), "PACIENTE", action, entityType, entityId, occurredAt));
    }

    @Transactional(readOnly = true)
    public AuditEventPageResponse list(StaffPrincipal principal, int page, int limit) {
        if (principal.role() != RolPersonal.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No tienes permiso para esta operación");
        }
        if (page < 0 || limit < 1 || limit > MAX_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAGING_INVALID", "La paginación no es válida");
        }
        Page<BitacoraEvento> events = repository.findAllByOrderByOcurridoEnDesc(
                PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "ocurridoEn")));
        List<AuditEventResponse> items = events.getContent().stream()
                .map(event -> new AuditEventResponse(event.getId(), event.getActorId(), event.getActorTipo(),
                        event.getActorRol(), event.getAccion(),
                        event.getEntidadTipo(), event.getEntidadId(), event.getOcurridoEn()))
                .toList();
        return new AuditEventPageResponse(items, page, limit, events.getTotalElements(), events.hasNext());
    }
}
