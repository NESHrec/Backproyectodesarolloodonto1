package com.clinicaserena.staff.service;

import com.clinicaserena.citas.entity.Cita;
import com.clinicaserena.citas.entity.EstadoCita;
import com.clinicaserena.citas.repository.CitaRepository;
import com.clinicaserena.common.exception.ApiException;
import com.clinicaserena.staff.dto.ReceptionAppointmentResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import com.clinicaserena.auditoria.service.BitacoraService;
import com.clinicaserena.staff.security.StaffPrincipal;

@Service
public class StaffAgendaService {

    private final CitaRepository citaRepository;
    private final BitacoraService audit;

    public StaffAgendaService(CitaRepository citaRepository, BitacoraService audit) {
        this.citaRepository = citaRepository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ReceptionAppointmentResponse> list(OffsetDateTime from, OffsetDateTime to,
                                                   EstadoCita status, int limit) {
        if (limit < 1 || limit > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LIMIT_INVALID", "El límite debe estar entre 1 y 100");
        }
        OffsetDateTime start = from == null ? OffsetDateTime.now(ZoneOffset.UTC).minusDays(1) : from;
        OffsetDateTime end = to == null ? start.plusDays(30) : to;
        if (!end.isAfter(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_RANGE_INVALID", "El rango de fechas no es válido");
        }
        return citaRepository.findForStaffAgenda(start, end, status, PageRequest.of(0, limit))
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReceptionAppointmentResponse registerArrival(String appointmentId, StaffPrincipal principal) {
        Cita cita = citaRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", "La cita no existe"));
        if (cita.getLlegadaEn() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "ARRIVAL_ALREADY_REGISTERED", "La llegada ya fue registrada");
        }
        if (cita.getEstado() == EstadoCita.CANCELADA || cita.getEstado() == EstadoCita.COMPLETADA) {
            throw new ApiException(HttpStatus.CONFLICT, "ARRIVAL_NOT_ALLOWED", "La cita no admite registro de llegada");
        }
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        cita.registrarLlegada(now, principal.accountId());
        ReceptionAppointmentResponse response=toResponse(citaRepository.saveAndFlush(cita));
        audit.record(principal,"APPOINTMENT_ARRIVAL_RECORDED","CITA",appointmentId,now);
        return response;
    }

    private ReceptionAppointmentResponse toResponse(Cita cita) {
        return new ReceptionAppointmentResponse(cita.getId(), cita.getPacienteId(), cita.getMedico().getId(),
                cita.getEspecialidad().getId(), cita.getProgramadaEn(), cita.getEstado(), cita.getLlegadaEn(),
                cita.getLlegadaPorPersonalId());
    }
}
