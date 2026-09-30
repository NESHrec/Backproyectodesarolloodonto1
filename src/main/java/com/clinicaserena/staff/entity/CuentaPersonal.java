package com.clinicaserena.staff.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "cuentas_personal")
public class CuentaPersonal {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "email_normalizado", length = 254, nullable = false, unique = true)
    private String emailNormalizado;

    @Column(name = "nombre_completo", length = 160, nullable = false)
    private String nombreCompleto;

    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RolPersonal rol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCuentaPersonal estado;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizada_en", nullable = false)
    private OffsetDateTime actualizadaEn;

    @Column(name = "medico_id", length = 36)
    private String medicoId;

    @Column(name = "medico_vinculado_en")
    private OffsetDateTime medicoVinculadoEn;

    @Column(name = "medico_vinculado_por", length = 36)
    private String medicoVinculadoPor;

    protected CuentaPersonal() {
    }

    public static CuentaPersonal crear(String id, String email, String nombre, String passwordHash,
                                       RolPersonal rol, OffsetDateTime ahora) {
        CuentaPersonal cuenta = new CuentaPersonal();
        cuenta.id = id;
        cuenta.emailNormalizado = email;
        cuenta.nombreCompleto = nombre;
        cuenta.passwordHash = passwordHash;
        cuenta.rol = rol;
        cuenta.estado = EstadoCuentaPersonal.ACTIVA;
        cuenta.creadoEn = ahora;
        cuenta.actualizadaEn = ahora;
        return cuenta;
    }

    public String getId() { return id; }
    public String getEmailNormalizado() { return emailNormalizado; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getPasswordHash() { return passwordHash; }
    public RolPersonal getRol() { return rol; }
    public EstadoCuentaPersonal getEstado() { return estado; }
    public String getMedicoId() { return medicoId; }
    public OffsetDateTime getMedicoVinculadoEn() { return medicoVinculadoEn; }
    public String getMedicoVinculadoPor() { return medicoVinculadoPor; }

    public void vincularMedico(String medicoId, String adminId, OffsetDateTime ahora) {
        this.medicoId = medicoId;
        this.medicoVinculadoEn = ahora;
        this.medicoVinculadoPor = adminId;
        this.actualizadaEn = ahora;
    }

    public void desvincularMedico(OffsetDateTime ahora) {
        this.medicoId = null;
        this.medicoVinculadoEn = null;
        this.medicoVinculadoPor = null;
        this.actualizadaEn = ahora;
    }
}
