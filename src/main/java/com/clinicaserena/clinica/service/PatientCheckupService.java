package com.clinicaserena.clinica.service;

import com.clinicaserena.auth.security.PatientPrincipal;
import com.clinicaserena.catalogo.entity.Medico;
import com.clinicaserena.catalogo.repository.MedicoRepository;
import com.clinicaserena.citas.entity.Cita;
import com.clinicaserena.citas.repository.CitaRepository;
import com.clinicaserena.clinica.dto.PatientCheckupResponse;
import com.clinicaserena.clinica.entity.AtencionClinica;
import com.clinicaserena.clinica.entity.RecetaItem;
import com.clinicaserena.clinica.repository.AtencionClinicaRepository;
import com.clinicaserena.clinica.repository.RecetaItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PatientCheckupService {

    private final AtencionClinicaRepository attentionRepository;
    private final RecetaItemRepository prescriptionItemRepository;
    private final CitaRepository appointmentRepository;
    private final MedicoRepository practitionerRepository;

    public PatientCheckupService(AtencionClinicaRepository attentionRepository,
                                 RecetaItemRepository prescriptionItemRepository,
                                 CitaRepository appointmentRepository,
                                 MedicoRepository practitionerRepository) {
        this.attentionRepository = attentionRepository;
        this.prescriptionItemRepository = prescriptionItemRepository;
        this.appointmentRepository = appointmentRepository;
        this.practitionerRepository = practitionerRepository;
    }

    /** La identidad proviene del principal y el filtro se aplica en la consulta por paciente. */
    @Transactional(readOnly = true)
    public List<PatientCheckupResponse> listOwn(PatientPrincipal principal) {
        List<AtencionClinica> attentions = attentionRepository
                .findByPacienteIdOrderByRegistradaEnDesc(principal.patientId());
        if (attentions.isEmpty()) return List.of();

        List<String> attentionIds = attentions.stream().map(AtencionClinica::getId).toList();
        Map<String, List<PatientCheckupResponse.PrescriptionItem>> prescriptions =
                prescriptionItemRepository.findByAtencionIdInOrderByAtencionIdAscOrdenAsc(attentionIds).stream()
                        .collect(Collectors.groupingBy(RecetaItem::getAtencionId, Collectors.mapping(
                                item -> new PatientCheckupResponse.PrescriptionItem(item.getId(), item.getOrden(),
                                        item.getMedicamento(), item.getDosis(), item.getFrecuencia(),
                                        item.getDuracion(), item.getIndicaciones()),
                                Collectors.toList())));
        Map<String, OffsetDateTime> scheduledByAppointment = appointmentRepository
                .findAllById(attentions.stream().map(AtencionClinica::getCitaId).toList()).stream()
                .collect(Collectors.toMap(Cita::getId, Cita::getProgramadaEn));
        Map<String, String> practitionerNames = practitionerRepository
                .findAllById(attentions.stream().map(AtencionClinica::getMedicoId).distinct().toList()).stream()
                .collect(Collectors.toMap(Medico::getId, Medico::getNombreCompleto));

        return attentions.stream().map(attention -> new PatientCheckupResponse(
                attention.getId(), attention.getCitaId(), scheduledByAppointment.get(attention.getCitaId()),
                attention.getRegistradaEn(), attention.getMedicoId(), practitionerNames.get(attention.getMedicoId()),
                attention.getMotivoConsulta(), attention.getHallazgos(), attention.getDiagnostico(),
                attention.getPlanTratamiento(), prescriptions.getOrDefault(attention.getId(), List.of())))
                .toList();
    }
}
