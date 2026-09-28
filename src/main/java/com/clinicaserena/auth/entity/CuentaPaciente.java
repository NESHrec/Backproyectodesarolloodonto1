package com.clinicaserena.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "cuentas_paciente")
public class CuentaPaciente {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false, unique = true)
    private Paciente paciente;

    @Column(name = "email_normalizado", nullable = false, unique = true, length = 254)
    private String emailNormalizado;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCuentaPaciente estado;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizada_en", nullable = false)
    private OffsetDateTime actualizadaEn;

    @Column(name = "email_verificado_en")
    private OffsetDateTime emailVerificadoEn;

    @Column(name = "nombre_completo", length = 160)
    private String nombreCompleto;

    protected CuentaPaciente() {
    }

    public static CuentaPaciente crear(String id, Paciente paciente, String email, String passwordHash,
                                        String nombreCompleto, OffsetDateTime ahora) {
        CuentaPaciente cuenta = new CuentaPaciente();
        cuenta.id = id;
        cuenta.paciente = paciente;
        cuenta.emailNormalizado = email;
        cuenta.passwordHash = passwordHash;
        cuenta.nombreCompleto = nombreCompleto;
        cuenta.estado = EstadoCuentaPaciente.ACTIVA;
        cuenta.creadoEn = ahora;
        cuenta.actualizadaEn = ahora;
        return cuenta;
    }

    public OffsetDateTime getEmailVerificadoEn() { return emailVerificadoEn; }
    public void verificarEmail(OffsetDateTime ahora) { emailVerificadoEn = ahora; actualizadaEn = ahora; }
    public void cambiarPassword(String hash, OffsetDateTime ahora) { passwordHash = hash; actualizadaEn = ahora; }

    public String getId() {
        return id;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public String getEmailNormalizado() {
        return emailNormalizado;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public EstadoCuentaPaciente getEstado() {
        return estado;
    }
}
