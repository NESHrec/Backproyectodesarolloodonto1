package com.clinicaserena.citas.entity;

import com.clinicaserena.catalogo.entity.BloqueDisponibilidad;
import com.clinicaserena.catalogo.entity.Especialidad;
import com.clinicaserena.catalogo.entity.Medico;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "citas")
public class Cita {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "paciente_id", length = 36, nullable = false, updatable = false)
    private String pacienteId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bloque_id", nullable = false, updatable = false)
    private BloqueDisponibilidad bloque;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_id", nullable = false, updatable = false)
    private Medico medico;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "especialidad_id", nullable = false, updatable = false)
    private Especialidad especialidad;

    @Column(name = "programada_en", nullable = false, updatable = false)
    private OffsetDateTime programadaEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCita estado;

    @Column(length = 1000)
    private String notas;

    @Column(name = "monto_centavos")
    private Long montoCentavos;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private OffsetDateTime creadaEn;

    @Column(name = "actualizada_en", nullable = false)
    private OffsetDateTime actualizadaEn;

    protected Cita() {
    }

    public Cita(String id, String pacienteId, BloqueDisponibilidad bloque, String notas, OffsetDateTime ahora) {
        this.id = id;
        this.pacienteId = pacienteId;
        this.bloque = bloque;
        this.medico = bloque.getMedico();
        this.especialidad = bloque.getMedico().getEspecialidad();
        this.programadaEn = bloque.getInicio();
        this.estado = EstadoCita.PENDIENTE;
        this.notas = notas;
        this.creadaEn = ahora;
        this.actualizadaEn = ahora;
    }

    public String getId() { return id; }
    public String getPacienteId() { return pacienteId; }
    public BloqueDisponibilidad getBloque() { return bloque; }
    public Medico getMedico() { return medico; }
    public Especialidad getEspecialidad() { return especialidad; }
    public OffsetDateTime getProgramadaEn() { return programadaEn; }
    public EstadoCita getEstado() { return estado; }
    public String getNotas() { return notas; }
    public Long getMontoCentavos() { return montoCentavos; }
    public OffsetDateTime getCreadaEn() { return creadaEn; }
    public OffsetDateTime getActualizadaEn() { return actualizadaEn; }

    public void cancelar(OffsetDateTime ahora) {
        this.estado = EstadoCita.CANCELADA;
        this.actualizadaEn = ahora;
    }
}
