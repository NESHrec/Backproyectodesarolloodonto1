package com.clinicaserena.clinica.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;

/**
 * Atención documentada de una cita. Paciente y profesional se copian de la cita
 * persistida y la base valida la combinación; el registro no admite modificaciones.
 */
@Entity
@Immutable
@Table(name = "atenciones_clinicas")
public class AtencionClinica {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "cita_id", length = 36, nullable = false, unique = true, updatable = false)
    private String citaId;

    @Column(name = "expediente_id", length = 36, nullable = false, updatable = false)
    private String expedienteId;

    @Column(name = "paciente_id", length = 36, nullable = false, updatable = false)
    private String pacienteId;

    @Column(name = "medico_id", length = 36, nullable = false, updatable = false)
    private String medicoId;

    @Column(name = "autor_personal_id", length = 36, nullable = false, updatable = false)
    private String autorPersonalId;

    @Column(name = "motivo_consulta", length = 1000, nullable = false, updatable = false)
    private String motivoConsulta;

    @Column(length = 2000, updatable = false)
    private String hallazgos;

    @Column(length = 1000, nullable = false, updatable = false)
    private String diagnostico;

    @Column(name = "plan_tratamiento", length = 2000, updatable = false)
    private String planTratamiento;

    @Column(name = "registrada_en", nullable = false, updatable = false)
    private OffsetDateTime registradaEn;

    protected AtencionClinica() {
    }

    public static AtencionClinica registrar(String id, String citaId, String expedienteId, String pacienteId,
                                            String medicoId, String autorPersonalId, String motivoConsulta,
                                            String hallazgos, String diagnostico, String planTratamiento,
                                            OffsetDateTime registradaEn) {
        AtencionClinica atencion = new AtencionClinica();
        atencion.id = id;
        atencion.citaId = citaId;
        atencion.expedienteId = expedienteId;
        atencion.pacienteId = pacienteId;
        atencion.medicoId = medicoId;
        atencion.autorPersonalId = autorPersonalId;
        atencion.motivoConsulta = motivoConsulta;
        atencion.hallazgos = hallazgos;
        atencion.diagnostico = diagnostico;
        atencion.planTratamiento = planTratamiento;
        atencion.registradaEn = registradaEn;
        return atencion;
    }

    public String getId() { return id; }
    public String getCitaId() { return citaId; }
    public String getExpedienteId() { return expedienteId; }
    public String getPacienteId() { return pacienteId; }
    public String getMedicoId() { return medicoId; }
    public String getAutorPersonalId() { return autorPersonalId; }
    public String getMotivoConsulta() { return motivoConsulta; }
    public String getHallazgos() { return hallazgos; }
    public String getDiagnostico() { return diagnostico; }
    public String getPlanTratamiento() { return planTratamiento; }
    public OffsetDateTime getRegistradaEn() { return registradaEn; }
}
