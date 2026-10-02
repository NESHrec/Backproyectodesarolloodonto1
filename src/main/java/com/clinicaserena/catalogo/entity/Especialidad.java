package com.clinicaserena.catalogo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
@Entity
@Table(name = "especialidades")
public class Especialidad {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, unique = true, length = 120)
    private String nombre;

    @Column(nullable = false, length = 500)
    private String descripcion;

    protected Especialidad() {
    }

    public static Especialidad crear(String id, String nombre, String descripcion) {
        Especialidad especialidad = new Especialidad();
        especialidad.id = Objects.requireNonNull(id);
        especialidad.nombre = Objects.requireNonNull(nombre);
        especialidad.descripcion = Objects.requireNonNull(descripcion);
        return especialidad;
    }

    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void actualizar(String nombre, String descripcion) {
        this.nombre = Objects.requireNonNull(nombre);
        this.descripcion = Objects.requireNonNull(descripcion);
    }
}
