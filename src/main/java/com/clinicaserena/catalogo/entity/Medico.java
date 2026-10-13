package com.clinicaserena.catalogo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;

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

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "foto_clave", length = 120)
    private String fotoClave;

    @Column(name = "foto_content_type", length = 40)
    private String fotoContentType;

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

    public boolean isActivo() { return activo; }
    public String getFotoClave() { return fotoClave; }
    public String getFotoContentType() { return fotoContentType; }

    public static Medico crear(String id, String nombre, Especialidad especialidad, String colegiado) {
        Medico medico = new Medico();
        medico.id = Objects.requireNonNull(id);
        medico.actualizar(nombre, especialidad, colegiado);
        medico.activo = true;
        return medico;
    }

    public void actualizar(String nombre, Especialidad especialidad, String colegiado) {
        this.nombreCompleto = Objects.requireNonNull(nombre);
        this.especialidad = Objects.requireNonNull(especialidad);
        this.numeroColegiado = Objects.requireNonNull(colegiado);
    }

    public void cambiarEstado(boolean activo) { this.activo = activo; }
    public void asignarFoto(String clave, String contentType) {
        this.fotoClave = Objects.requireNonNull(clave);
        this.fotoContentType = Objects.requireNonNull(contentType);
    }
    public void quitarFoto() { this.fotoClave = null; this.fotoContentType = null; }
}
