package com.clinicaserena.recepcion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** Datos administrativos mínimos; no representa ni crea una cuenta de paciente. */
@Entity
@Table(name = "pacientes_administrativos")
public class PacienteAdministrativo {

    @Id
    @Column(name = "paciente_id", length = 36, nullable = false, updatable = false)
    private String pacienteId;

    @Column(name = "nombre_completo", length = 160, nullable = false, updatable = false)
    private String nombreCompleto;

    @Column(length = 40, nullable = false, updatable = false)
    private String telefono;

    @Column(name = "telefono_normalizado", length = 20, nullable = false, updatable = false)
    private String telefonoNormalizado;

    @Column(name = "email_contacto", length = 254, updatable = false)
    private String emailContacto;

    @Column(name = "email_contacto_normalizado", length = 254, updatable = false)
    private String emailContactoNormalizado;

    @Column(name = "creado_por_personal_id", length = 36, nullable = false, updatable = false)
    private String creadoPorPersonalId;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    protected PacienteAdministrativo() {
    }

    public static PacienteAdministrativo crear(String pacienteId, String nombreCompleto, String telefono,
                                               String telefonoNormalizado, String emailContacto,
                                               String emailContactoNormalizado, String creadoPorPersonalId,
                                               OffsetDateTime creadoEn) {
        PacienteAdministrativo record = new PacienteAdministrativo();
        record.pacienteId = pacienteId;
        record.nombreCompleto = nombreCompleto;
        record.telefono = telefono;
        record.telefonoNormalizado = telefonoNormalizado;
        record.emailContacto = emailContacto;
        record.emailContactoNormalizado = emailContactoNormalizado;
        record.creadoPorPersonalId = creadoPorPersonalId;
        record.creadoEn = creadoEn;
        return record;
    }

    public String getPacienteId() { return pacienteId; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getTelefono() { return telefono; }
    public String getEmailContacto() { return emailContacto; }
    public String getCreadoPorPersonalId() { return creadoPorPersonalId; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
}
