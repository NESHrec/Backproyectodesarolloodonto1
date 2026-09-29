package com.clinicaserena.staff.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "sesiones_personal")
public class SesionPersonal {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private CuentaPersonal cuenta;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private OffsetDateTime expiraEn;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private OffsetDateTime creadaEn;

    @Column(name = "revocada_en")
    private OffsetDateTime revocadaEn;

    protected SesionPersonal() {
    }

    public static SesionPersonal crear(String id, CuentaPersonal cuenta, String tokenHash,
                                       OffsetDateTime expiraEn, OffsetDateTime creadaEn) {
        SesionPersonal sesion = new SesionPersonal();
        sesion.id = id;
        sesion.cuenta = cuenta;
        sesion.tokenHash = tokenHash;
        sesion.expiraEn = expiraEn;
        sesion.creadaEn = creadaEn;
        return sesion;
    }

    public void revocar(OffsetDateTime ahora) { revocadaEn = ahora; }
    public CuentaPersonal getCuenta() { return cuenta; }
    public String getTokenHash() { return tokenHash; }
    public OffsetDateTime getExpiraEn() { return expiraEn; }
    public OffsetDateTime getRevocadaEn() { return revocadaEn; }
}
