package com.clinicaserena.auth.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "tokens_cuenta_paciente")
public class TokenCuentaPaciente {
    @Id @Column(length = 36, nullable = false, updatable = false) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cuenta_id") private CuentaPaciente cuenta;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private TipoTokenCuenta tipo;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "expira_en", nullable = false) private OffsetDateTime expiraEn;
    @Column(name = "creado_en", nullable = false) private OffsetDateTime creadoEn;
    @Column(name = "usado_en") private OffsetDateTime usadoEn;

    protected TokenCuentaPaciente() {}
    public static TokenCuentaPaciente crear(String id, CuentaPaciente cuenta, TipoTokenCuenta tipo,
            String hash, OffsetDateTime expira, OffsetDateTime ahora) {
        TokenCuentaPaciente token = new TokenCuentaPaciente(); token.id=id; token.cuenta=cuenta;
        token.tipo=tipo; token.tokenHash=hash; token.expiraEn=expira; token.creadoEn=ahora; return token;
    }
    public CuentaPaciente getCuenta() { return cuenta; }
    public OffsetDateTime getExpiraEn() { return expiraEn; }
    public OffsetDateTime getUsadoEn() { return usadoEn; }
    public void usar(OffsetDateTime ahora) { usadoEn = ahora; }
}
