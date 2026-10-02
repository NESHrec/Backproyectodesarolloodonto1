package com.clinicaserena.clinica.service;

import com.clinicaserena.auth.security.PatientPrincipal;
import com.clinicaserena.catalogo.entity.Medico;
import com.clinicaserena.catalogo.repository.MedicoRepository;
import com.clinicaserena.citas.entity.Cita;
import com.clinicaserena.citas.repository.CitaRepository;
import com.clinicaserena.clinica.dto.PatientPrescriptionResponse;
import com.clinicaserena.clinica.entity.AtencionClinica;
import com.clinicaserena.clinica.entity.RecetaItem;
import com.clinicaserena.clinica.repository.AtencionClinicaRepository;
import com.clinicaserena.clinica.repository.RecetaItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PatientPrescriptionService {

    private final AtencionClinicaRepository attentionRepository;
    private final RecetaItemRepository prescriptionItemRepository;
    private final CitaRepository appointmentRepository;
    private final MedicoRepository practitionerRepository;

    public PatientPrescriptionService(AtencionClinicaRepository attentionRepository,
                                      RecetaItemRepository prescriptionItemRepository,
                                      CitaRepository appointmentRepository,
                                      MedicoRepository practitionerRepository) {
        this.attentionRepository = attentionRepository;
        this.prescriptionItemRepository = prescriptionItemRepository;
        this.appointmentRepository = appointmentRepository;
        this.practitionerRepository = practitionerRepository;
    }

    /** La identidad se recibe del principal; no existe entrada HTTP para elegir otro paciente. */
    @Transactional(readOnly = true)
    public List<PatientPrescriptionResponse> listOwn(PatientPrincipal principal) {
        List<AtencionClinica> attentions = attentionRepository
                .findPrescriptionsByPacienteId(principal.patientId());
        if (attentions.isEmpty()) return List.of();

        List<String> attentionIds = attentions.stream().map(AtencionClinica::getId).toList();
        Map<String, List<PatientPrescriptionResponse.PrescriptionItem>> itemsByAttention =
                prescriptionItemRepository.findByAtencionIdInOrderByAtencionIdAscOrdenAsc(attentionIds).stream()
                        .collect(Collectors.groupingBy(RecetaItem::getAtencionId, Collectors.mapping(
                                item -> new PatientPrescriptionResponse.PrescriptionItem(item.getId(), item.getOrden(),
                                        item.getMedicamento(), item.getDosis(), item.getFrecuencia(),
                                        item.getDuracion(), item.getIndicaciones()),
                                Collectors.toList())));
        Map<String, OffsetDateTime> scheduledByAppointment = appointmentRepository
                .findAllById(attentions.stream().map(AtencionClinica::getCitaId).distinct().toList()).stream()
                .collect(Collectors.toMap(Cita::getId, Cita::getProgramadaEn));
        Map<String, String> practitionerNames = practitionerRepository
                .findAllById(attentions.stream().map(AtencionClinica::getMedicoId).distinct().toList()).stream()
                .collect(Collectors.toMap(Medico::getId, Medico::getNombreCompleto));

        return attentions.stream().map(attention -> new PatientPrescriptionResponse(
                attention.getId(), attention.getCitaId(), scheduledByAppointment.get(attention.getCitaId()),
                attention.getRegistradaEn(), attention.getMedicoId(),
                practitionerNames.get(attention.getMedicoId()),
                itemsByAttention.getOrDefault(attention.getId(), List.of())))
                // Respaldo defensivo del filtro del repositorio: una atención sin medicamentos nunca se expone.
                .filter(response -> !response.items().isEmpty())
                .toList();
    }
}
