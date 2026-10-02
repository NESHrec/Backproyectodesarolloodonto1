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
        return disponible;
    }

    public void reservar() {
        this.disponible = false;
    }

    public void liberar() {
        this.disponible = true;
    }
}
