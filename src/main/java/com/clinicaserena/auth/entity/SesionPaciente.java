package com.clinicaserena.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "sesiones_paciente")
public class SesionPaciente {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private CuentaPaciente cuenta;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private OffsetDateTime expiraEn;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private OffsetDateTime creadaEn;

    @Column(name = "revocada_en")
    private OffsetDateTime revocadaEn;

    protected SesionPaciente() {
    }

    public static SesionPaciente crear(
            String id,
            CuentaPaciente cuenta,
            String tokenHash,
            OffsetDateTime expiraEn,
            OffsetDateTime creadaEn
    ) {
        SesionPaciente session = new SesionPaciente();
        session.id = id;
        session.cuenta = cuenta;
        session.tokenHash = tokenHash;
        session.expiraEn = expiraEn;
        session.creadaEn = creadaEn;
        return session;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public CuentaPaciente getCuenta() {
        return cuenta;
    }

    public OffsetDateTime getExpiraEn() {
        return expiraEn;
    }

    public OffsetDateTime getRevocadaEn() {
        return revocadaEn;
    }

    public void revocar(OffsetDateTime ahora) {
        this.revocadaEn = ahora;
    }
}
