package com.clinicaserena.catalogo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "bloques_disponibilidad")
public class BloqueDisponibilidad {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    @Column(name = "inicio", nullable = false)
    private OffsetDateTime inicio;

    @Column(name = "fin", nullable = false)
    private OffsetDateTime fin;

    @Column(nullable = false)
    private boolean disponible;

    @Column(name = "retirado_en")
    private OffsetDateTime retiradoEn;

    @Column(name = "retirado_por_personal_id", length = 36)
    private String retiradoPorPersonalId;

    protected BloqueDisponibilidad() {
    }

    public static BloqueDisponibilidad crear(String id, Medico medico, OffsetDateTime inicio,
                                             OffsetDateTime fin) {
        BloqueDisponibilidad bloque = new BloqueDisponibilidad();
        bloque.id = Objects.requireNonNull(id);
        bloque.medico = Objects.requireNonNull(medico);
        bloque.inicio = Objects.requireNonNull(inicio);
        bloque.fin = Objects.requireNonNull(fin);
        bloque.disponible = true;
        return bloque;
    }

    public String getId() {
        return id;
    }

    public Medico getMedico() {
        return medico;
    }

    public OffsetDateTime getInicio() {
        return inicio;
    }

    public OffsetDateTime getFin() {
        return fin;
    }

    public boolean isDisponible() {
        return disponible && retiradoEn == null;
    }

    public boolean isRetirado() { return retiradoEn != null; }

    public void reprogramar(OffsetDateTime inicio, OffsetDateTime fin) {
        this.inicio = Objects.requireNonNull(inicio);
        this.fin = Objects.requireNonNull(fin);
    }

    public void retirar(String actorId, OffsetDateTime ahora) {
        this.disponible = false;
        this.retiradoPorPersonalId = Objects.requireNonNull(actorId);
        this.retiradoEn = Objects.requireNonNull(ahora);
    }

    public void reservar() {
        this.disponible = false;
    }

    public void liberar() {
        if (retiradoEn == null) this.disponible = true;
    }
}
