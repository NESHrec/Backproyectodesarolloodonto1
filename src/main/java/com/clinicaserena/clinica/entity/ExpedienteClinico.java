package com.clinicaserena.clinica.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;

/** Expediente único por paciente; se crea con la primera atención documentada. */
@Entity
@Immutable
@Table(name = "expedientes_clinicos")
public class ExpedienteClinico {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "paciente_id", length = 36, nullable = false, unique = true, updatable = false)
    private String pacienteId;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "creado_por_personal_id", length = 36, nullable = false, updatable = false)
    private String creadoPorPersonalId;

    protected ExpedienteClinico() {
    }

    public String getId() { return id; }
    public String getPacienteId() { return pacienteId; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
}
