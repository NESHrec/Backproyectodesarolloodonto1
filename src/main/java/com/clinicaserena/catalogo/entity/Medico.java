package com.clinicaserena.catalogo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "medicos")
public class Medico {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "nombre_completo", nullable = false, length = 160)
    private String nombreCompleto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "especialidad_id", nullable = false)
    private Especialidad especialidad;

    @Column(name = "numero_colegiado", nullable = false, unique = true, length = 50)
    private String numeroColegiado;

    protected Medico() {
    }

    public String getId() {
        return id;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public Especialidad getEspecialidad() {
        return especialidad;
    }

    public String getNumeroColegiado() {
        return numeroColegiado;
    }
}
