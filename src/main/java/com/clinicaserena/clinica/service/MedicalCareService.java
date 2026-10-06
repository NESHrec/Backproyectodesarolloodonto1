package com.clinicaserena.clinica.service;

import com.clinicaserena.auth.entity.CuentaPaciente;
import com.clinicaserena.auth.entity.Paciente;
import com.clinicaserena.auth.repository.CuentaPacienteRepository;
import com.clinicaserena.auth.repository.PacienteRepository;
import com.clinicaserena.catalogo.entity.Medico;
import com.clinicaserena.catalogo.repository.MedicoRepository;
import com.clinicaserena.citas.entity.Cita;
import com.clinicaserena.citas.entity.EstadoCita;
import com.clinicaserena.citas.repository.CitaRepository;
import com.clinicaserena.clinica.dto.AttentionResponse;
import com.clinicaserena.clinica.dto.ClinicalRecordResponse;
import com.clinicaserena.clinica.dto.MedicalAppointmentDetailResponse;
import com.clinicaserena.clinica.dto.MedicalAppointmentResponse;
import com.clinicaserena.clinica.dto.MedicalAppointmentResponse.AttentionBlocker;
import com.clinicaserena.clinica.dto.RecordAttentionRequest;
import com.clinicaserena.clinica.dto.CreateAddendumRequest;
import com.clinicaserena.clinica.dto.UpdateClinicalProfileRequest;
import com.clinicaserena.clinica.entity.AdendaAtencion;
import com.clinicaserena.clinica.entity.VersionPerfilClinico;
import com.clinicaserena.clinica.entity.AtencionClinica;
import com.clinicaserena.clinica.entity.ExpedienteClinico;
import com.clinicaserena.clinica.entity.RecetaItem;
import com.clinicaserena.clinica.repository.AtencionClinicaRepository;
import com.clinicaserena.clinica.repository.ExpedienteClinicoRepository;
import com.clinicaserena.clinica.repository.RecetaItemRepository;
import com.clinicaserena.clinica.repository.AdendaAtencionRepository;
import com.clinicaserena.clinica.repository.VersionPerfilClinicoRepository;
import com.clinicaserena.common.exception.ApiException;
import com.clinicaserena.staff.entity.CuentaPersonal;
import com.clinicaserena.staff.entity.EstadoCuentaPersonal;
import com.clinicaserena.staff.entity.RolPersonal;
import com.clinicaserena.staff.repository.CuentaPersonalRepository;
import com.clinicaserena.staff.security.StaffPrincipal;
import com.clinicaserena.auth.security.PatientPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Operaciones clínicas del profesional autenticado. El profesional se resuelve
 * siempre desde la cuenta del principal; paciente y cita se obtienen de la cita
 * persistida. Una cita de otro profesional se trata como inexistente.
 */
@Service
public class MedicalCareService {

    /** Estados en los que la cita aún puede documentarse; al documentarla pasa a COMPLETADA. */
    static final Set<EstadoCita> DOCUMENTABLE_STATES = Set.of(EstadoCita.PENDIENTE, EstadoCita.CONFIRMADA);
    private static final int MAX_LIMIT = 100;

    private final CuentaPersonalRepository staffRepository;
    private final CitaRepository citaRepository;
    private final PacienteRepository pacienteRepository;
    private final CuentaPacienteRepository cuentaPacienteRepository;
    private final MedicoRepository medicoRepository;
    private final ExpedienteClinicoRepository expedienteRepository;
    private final AtencionClinicaRepository atencionRepository;
    private final RecetaItemRepository recetaRepository;
    private final AdendaAtencionRepository adendaRepository;
    private final VersionPerfilClinicoRepository perfilRepository;
    private final Clock clock;

    public MedicalCareService(CuentaPersonalRepository staffRepository, CitaRepository citaRepository,
                              PacienteRepository pacienteRepository, CuentaPacienteRepository cuentaPacienteRepository,
                              MedicoRepository medicoRepository, ExpedienteClinicoRepository expedienteRepository,
                              AtencionClinicaRepository atencionRepository, RecetaItemRepository recetaRepository,
                              AdendaAtencionRepository adendaRepository, VersionPerfilClinicoRepository perfilRepository,
                              Clock clock) {
        this.clock = clock;
        this.staffRepository = staffRepository;
        this.citaRepository = citaRepository;
        this.pacienteRepository = pacienteRepository;
        this.cuentaPacienteRepository = cuentaPacienteRepository;
        this.medicoRepository = medicoRepository;
        this.expedienteRepository = expedienteRepository;
        this.atencionRepository = atencionRepository;
        this.recetaRepository = recetaRepository;
        this.adendaRepository = adendaRepository;
        this.perfilRepository = perfilRepository;
    }

    @Transactional(readOnly = true)
    public List<MedicalAppointmentResponse> listOwnAppointments(StaffPrincipal principal, OffsetDateTime from,
                                                                OffsetDateTime to, EstadoCita status, int limit) {
        String medicoId = requirePractitioner(principal);
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LIMIT_INVALID", "El límite debe estar entre 1 y 100");
        }
        OffsetDateTime start = from == null ? OffsetDateTime.now(clock).minusDays(7) : from;
        OffsetDateTime end = to == null ? start.plusDays(67) : to;
        if (!end.isAfter(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_RANGE_INVALID", "El rango de fechas no es válido");
        }
        List<Cita> citas = citaRepository.findForPractitioner(medicoId, start, end, status, PageRequest.of(0, limit));
        return toAppointmentResponses(citas);
    }

    @Transactional(readOnly = true)
    public MedicalAppointmentDetailResponse getOwnAppointment(StaffPrincipal principal, String citaId) {
        String medicoId = requirePractitioner(principal);
        Cita cita = findOwnAppointment(citaId, medicoId);
        AttentionResponse attention = atencionRepository.findByCitaId(cita.getId())
                .map(atencion -> toAttentionResponses(List.of(atencion)).get(0))
                .orElse(null);
        return new MedicalAppointmentDetailResponse(toAppointmentResponses(List.of(cita)).get(0), attention);
    }

    @Transactional(readOnly = true)
    public ClinicalRecordResponse getRecordForAppointment(StaffPrincipal principal, String citaId) {
        String medicoId = requirePractitioner(principal);
        Cita cita = findOwnAppointment(citaId, medicoId);
        Paciente paciente = requirePatient(cita);
        return buildClinicalRecord(paciente);
    }

    @Transactional(readOnly = true)
    public ClinicalRecordResponse getOwnPatientRecord(PatientPrincipal principal) {
        Paciente paciente=pacienteRepository.findById(principal.patientId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"PATIENT_NOT_FOUND","El paciente no existe"));
        return buildClinicalRecord(paciente);
    }

    private ClinicalRecordResponse buildClinicalRecord(Paciente paciente) {
        String fullName = cuentaPacienteRepository.findByPacienteIdIn(List.of(paciente.getId())).stream()
                .findFirst().map(CuentaPaciente::getNombreCompleto).orElse(null);
        ExpedienteClinico expediente = expedienteRepository.findByPacienteId(paciente.getId()).orElse(null);
        List<AttentionResponse> attentions = toAttentionResponses(
                atencionRepository.findByPacienteIdOrderByRegistradaEnDesc(paciente.getId()));
        List<ClinicalRecordResponse.ClinicalProfileResponse> profiles = toProfileResponses(
                perfilRepository.findByPacienteIdOrderByRegistradaEnDescSecuenciaDesc(paciente.getId()));
        return new ClinicalRecordResponse(
                new ClinicalRecordResponse.PatientSummary(paciente.getId(), fullName, paciente.getEstado(),
                        paciente.getCreadoEn()),
                expediente == null ? null : expediente.getId(),
                expediente == null ? null : expediente.getCreadoEn(),
                profiles.isEmpty() ? null : profiles.get(0), profiles,
                attentions);
    }

    @Transactional
    public ClinicalRecordResponse.ClinicalProfileResponse updateClinicalProfile(StaffPrincipal principal, String citaId,
                                                                                 UpdateClinicalProfileRequest request) {
        String medicoId = requirePractitioner(principal);
        Cita cita = findOwnAppointment(citaId, medicoId);
        Paciente paciente = requirePatient(cita);
        String allergies=optional(request.allergies()), conditions=optional(request.relevantConditions());
        String medications=optional(request.currentMedications()), history=optional(request.dentalHistory());
        if (allergies==null && conditions==null && medications==null && history==null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,"CLINICAL_PROFILE_EMPTY","Indica al menos un dato clínico");
        }
        OffsetDateTime now=OffsetDateTime.now(clock);
        expedienteRepository.insertIfAbsent(UUID.randomUUID().toString(),paciente.getId(),now,principal.accountId());
        ExpedienteClinico expediente=expedienteRepository.findByPacienteId(paciente.getId()).orElseThrow();
        VersionPerfilClinico saved=perfilRepository.saveAndFlush(VersionPerfilClinico.registrar(UUID.randomUUID().toString(),
                expediente.getId(),paciente.getId(),allergies,conditions,medications,history,principal.accountId(),now));
        return toProfileResponses(List.of(saved)).get(0);
    }

    @Transactional
    public AttentionResponse.AddendumResponse addAddendum(StaffPrincipal principal, String appointmentId,
                                                           String attentionId, CreateAddendumRequest request) {
        String medicoId=requirePractitioner(principal);
        Cita cita=findOwnAppointment(appointmentId,medicoId);
        AtencionClinica attention=atencionRepository.findById(attentionId)
                .filter(a -> a.getCitaId().equals(cita.getId()) && a.getMedicoId().equals(medicoId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"ATTENTION_NOT_FOUND","La atención no existe"));
        String text=request.text().trim(), reason=request.reason().trim();
        if(text.length()<3 || reason.length()<3) throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Texto y motivo son obligatorios");
        AdendaAtencion saved=adendaRepository.saveAndFlush(AdendaAtencion.registrar(UUID.randomUUID().toString(),attention.getId(),text,reason,principal.accountId(),OffsetDateTime.now(clock)));
        return new AttentionResponse.AddendumResponse(saved.getId(),saved.getTexto(),saved.getMotivo(),saved.getAutorPersonalId(),
                staffRepository.findById(saved.getAutorPersonalId()).map(CuentaPersonal::getNombreCompleto).orElse(null),saved.getRegistradaEn());
    }

    @Transactional
    public AttentionResponse recordAttention(StaffPrincipal principal, String citaId, RecordAttentionRequest request) {
        String medicoId = requirePractitioner(principal);
        validateIdentifier(citaId);
        validateTrimmedContent(request);
        // El bloqueo serializa envíos simultáneos de la misma cita; uq_atencion_cita es el respaldo.
        Cita cita = citaRepository.findByIdForUpdate(citaId)
                .filter(found -> found.getMedico().getId().equals(medicoId))
                .orElseThrow(MedicalCareService::appointmentNotFound);
        // Las reglas se evalúan con la fila bloqueada: una llegada o atención concurrente no puede
        // intercalarse entre la comprobación y la escritura.
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<AttentionBlocker> blockers = blockers(cita, atencionRepository.existsByCitaId(cita.getId()), now);
        if (!blockers.isEmpty()) throw blockerException(blockers.get(0));
        Paciente paciente = requirePatient(cita);

        expedienteRepository.insertIfAbsent(UUID.randomUUID().toString(), paciente.getId(), now,
                principal.accountId());
        ExpedienteClinico expediente = expedienteRepository.findByPacienteId(paciente.getId())
                .orElseThrow(() -> new IllegalStateException("expediente no disponible"));

        AtencionClinica atencion = AtencionClinica.registrar(UUID.randomUUID().toString(), cita.getId(),
                expediente.getId(), paciente.getId(), medicoId, principal.accountId(),
                request.reason().trim(), optional(request.findings()), request.diagnosis().trim(),
                optional(request.treatmentPlan()), now);
        List<RecetaItem> items = new ArrayList<>();
        for (int index = 0; index < request.prescription().size(); index++) {
            RecordAttentionRequest.PrescriptionItemRequest item = request.prescription().get(index);
            items.add(RecetaItem.registrar(UUID.randomUUID().toString(), atencion.getId(), (short) (index + 1),
                    item.medicine().trim(), item.dose().trim(), item.frequency().trim(), item.duration().trim(),
                    optional(item.instructions())));
        }
        try {
            atencionRepository.saveAndFlush(atencion);
            recetaRepository.saveAllAndFlush(items);
            cita.completar(now);
            citaRepository.saveAndFlush(cita);
        } catch (DataIntegrityViolationException collision) {
            throw attentionAlreadyRecorded();
        }
        return toAttentionResponses(List.of(atencion)).get(0);
    }

    /** Devuelve el profesional vinculado a la cuenta autenticada o rechaza la solicitud. */
    private String requirePractitioner(StaffPrincipal principal) {
        CuentaPersonal account = staffRepository.findById(principal.accountId())
                .filter(found -> found.getEstado() == EstadoCuentaPersonal.ACTIVA)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                        "Autenticación requerida"));
        if (account.getRol() != RolPersonal.MEDICO) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No tienes permiso para esta operación");
        }
        if (account.getMedicoId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PRACTITIONER_LINK_REQUIRED",
                    "La cuenta médica aún no está vinculada a un profesional");
        }
        return account.getMedicoId();
    }

    private Cita findOwnAppointment(String citaId, String medicoId) {
        validateIdentifier(citaId);
        return citaRepository.findById(citaId)
                .filter(cita -> cita.getMedico().getId().equals(medicoId))
                .orElseThrow(MedicalCareService::appointmentNotFound);
    }

    private Paciente requirePatient(Cita cita) {
        return pacienteRepository.findById(cita.getPacienteId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "PATIENT_RECORD_UNAVAILABLE",
                        "La cita no está asociada a un paciente registrado"));
    }

    /**
     * Reglas del flujo presencial, en orden de prioridad: sin atención previa, estado
     * PENDIENTE o CONFIRMADA, hora programada ya comenzada y llegada registrada.
     */
    static List<AttentionBlocker> blockers(Cita cita, boolean attentionRecorded, OffsetDateTime now) {
        List<AttentionBlocker> blockers = new ArrayList<>();
        if (attentionRecorded) blockers.add(AttentionBlocker.ATTENTION_ALREADY_RECORDED);
        if (!DOCUMENTABLE_STATES.contains(cita.getEstado())) blockers.add(AttentionBlocker.STATUS_NOT_DOCUMENTABLE);
        if (cita.getProgramadaEn().toInstant().isAfter(now.toInstant())) blockers.add(AttentionBlocker.NOT_STARTED);
        if (cita.getLlegadaEn() == null) blockers.add(AttentionBlocker.ARRIVAL_NOT_REGISTERED);
        return blockers;
    }

    private static ApiException blockerException(AttentionBlocker blocker) {
        return switch (blocker) {
            case ATTENTION_ALREADY_RECORDED -> attentionAlreadyRecorded();
            case STATUS_NOT_DOCUMENTABLE -> new ApiException(HttpStatus.CONFLICT, "APPOINTMENT_NOT_DOCUMENTABLE",
                    "La cita no admite registrar una atención en su estado actual");
            case NOT_STARTED -> new ApiException(HttpStatus.CONFLICT, "APPOINTMENT_NOT_STARTED",
                    "La atención solo puede registrarse cuando comienza la hora programada de la cita");
            case ARRIVAL_NOT_REGISTERED -> new ApiException(HttpStatus.CONFLICT, "ARRIVAL_NOT_REGISTERED",
                    "Recepción debe registrar la llegada del paciente antes de documentar la atención");
        };
    }

    private List<MedicalAppointmentResponse> toAppointmentResponses(List<Cita> citas) {
        if (citas.isEmpty()) return List.of();
        Set<String> documented = Set.copyOf(atencionRepository.findDocumentedCitaIds(
                citas.stream().map(Cita::getId).toList()));
        Map<String, String> patientNames = patientNames(citas.stream().map(Cita::getPacienteId).toList());
        OffsetDateTime now = OffsetDateTime.now(clock);
        return citas.stream().map(cita -> {
            boolean recorded = documented.contains(cita.getId());
            List<AttentionBlocker> blockers = blockers(cita, recorded, now);
            return new MedicalAppointmentResponse(cita.getId(), cita.getPacienteId(),
                    patientNames.get(cita.getPacienteId()), cita.getMedico().getId(), cita.getEspecialidad().getId(),
                    cita.getEspecialidad().getNombre(), cita.getProgramadaEn(), cita.getEstado(), cita.getNotas(),
                    cita.getLlegadaEn(), recorded, blockers.isEmpty(), blockers);
        }).toList();
    }

    private List<AttentionResponse> toAttentionResponses(List<AtencionClinica> atenciones) {
        if (atenciones.isEmpty()) return List.of();
        Map<String, List<AttentionResponse.PrescriptionItemResponse>> items = recetaRepository
                .findByAtencionIdInOrderByAtencionIdAscOrdenAsc(ids(atenciones, AtencionClinica::getId)).stream()
                .collect(Collectors.groupingBy(RecetaItem::getAtencionId, Collectors.mapping(
                        item -> new AttentionResponse.PrescriptionItemResponse(item.getOrden(), item.getMedicamento(),
                                item.getDosis(), item.getFrecuencia(), item.getDuracion(), item.getIndicaciones()),
                        Collectors.toList())));
        Map<String, String> practitioners = medicoRepository.findAllById(ids(atenciones, AtencionClinica::getMedicoId))
                .stream().collect(Collectors.toMap(Medico::getId, Medico::getNombreCompleto));
        Map<String, String> authors = staffRepository.findAllById(ids(atenciones, AtencionClinica::getAutorPersonalId))
                .stream().collect(Collectors.toMap(CuentaPersonal::getId, CuentaPersonal::getNombreCompleto));
        Map<String, OffsetDateTime> scheduled = citaRepository.findAllById(ids(atenciones, AtencionClinica::getCitaId))
                .stream().collect(Collectors.toMap(Cita::getId, Cita::getProgramadaEn));
        Map<String,List<AdendaAtencion>> addenda=adendaRepository.findByAtencionIdInOrderByRegistradaEnAscIdAsc(ids(atenciones,AtencionClinica::getId))
                .stream().collect(Collectors.groupingBy(AdendaAtencion::getAtencionId));
        Set<String> addendumAuthors=addenda.values().stream().flatMap(List::stream).map(AdendaAtencion::getAutorPersonalId).collect(Collectors.toSet());
        Map<String,String> addendumAuthorNames=staffRepository.findAllById(addendumAuthors).stream().collect(Collectors.toMap(CuentaPersonal::getId,CuentaPersonal::getNombreCompleto));
        return atenciones.stream().map(atencion -> new AttentionResponse(atencion.getId(), atencion.getCitaId(),
                scheduled.get(atencion.getCitaId()), atencion.getMedicoId(), practitioners.get(atencion.getMedicoId()),
                atencion.getAutorPersonalId(), authors.get(atencion.getAutorPersonalId()),
                atencion.getMotivoConsulta(), atencion.getHallazgos(), atencion.getDiagnostico(),
                atencion.getPlanTratamiento(), atencion.getRegistradaEn(),
                items.getOrDefault(atencion.getId(), List.of()), addenda.getOrDefault(atencion.getId(),List.of()).stream()
                    .map(a -> new AttentionResponse.AddendumResponse(a.getId(),a.getTexto(),a.getMotivo(),a.getAutorPersonalId(),addendumAuthorNames.get(a.getAutorPersonalId()),a.getRegistradaEn())).toList())).toList();
    }

    private List<ClinicalRecordResponse.ClinicalProfileResponse> toProfileResponses(List<VersionPerfilClinico> versions) {
        Map<String,String> names=staffRepository.findAllById(ids(versions,VersionPerfilClinico::getAutorPersonalId)).stream()
                .collect(Collectors.toMap(CuentaPersonal::getId,CuentaPersonal::getNombreCompleto));
        return versions.stream().map(v -> new ClinicalRecordResponse.ClinicalProfileResponse(v.getId(),v.getAlergias(),
                v.getCondicionesRelevantes(),v.getMedicamentosActuales(),v.getAntecedentesOdontologicos(),v.getAutorPersonalId(),
                names.get(v.getAutorPersonalId()),v.getRegistradaEn())).toList();
    }

    private Map<String, String> patientNames(Collection<String> pacienteIds) {
        return cuentaPacienteRepository.findByPacienteIdIn(Set.copyOf(pacienteIds)).stream()
                .filter(cuenta -> cuenta.getNombreCompleto() != null)
                .collect(Collectors.toMap(cuenta -> cuenta.getPaciente().getId(), CuentaPaciente::getNombreCompleto,
                        (first, second) -> first));
    }

    private static <T> List<String> ids(List<T> values, Function<T, String> getter) {
        return values.stream().map(getter).filter(Objects::nonNull).distinct().toList();
    }

    /** Bean Validation mide el texto sin recortar; aquí se exige el mínimo sobre el contenido real. */
    private static void validateTrimmedContent(RecordAttentionRequest request) {
        List<String> invalid = new ArrayList<>();
        if (request.reason().trim().length() < 3) invalid.add("reason");
        if (request.diagnosis().trim().length() < 3) invalid.add("diagnosis");
        for (int index = 0; index < request.prescription().size(); index++) {
            if (request.prescription().get(index).medicine().trim().length() < 2) {
                invalid.add("prescription[" + index + "].medicine");
            }
        }
        if (!invalid.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Datos inválidos: " + String.join(", ", invalid));
        }
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateIdentifier(String value) {
        if (value == null || value.isBlank() || value.length() > 36) throw appointmentNotFound();
    }

    private static ApiException appointmentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", "La cita no existe");
    }

    private static ApiException attentionAlreadyRecorded() {
        return new ApiException(HttpStatus.CONFLICT, "ATTENTION_ALREADY_RECORDED",
                "La cita ya tiene una atención registrada");
    }
}
