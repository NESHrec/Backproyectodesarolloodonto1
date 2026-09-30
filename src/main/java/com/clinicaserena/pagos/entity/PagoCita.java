package com.clinicaserena.pagos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "pagos_citas")
public class PagoCita {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "cita_id", length = 36, nullable = false, updatable = false)
    private String citaId;

    @Column(name = "registrado_por_personal_id", length = 36, nullable = false, updatable = false)
    private String registradoPorPersonalId;

    @Column(name = "monto_centavos", nullable = false, updatable = false)
    private Long montoCentavos;

    @Column(length = 3, nullable = false, updatable = false)
    private String moneda;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private MetodoPago metodo;

    @Column(length = 120, updatable = false)
    private String referencia;

    @Column(name = "idempotency_key", length = 80, nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "registrado_en", nullable = false, updatable = false)
    private OffsetDateTime registradoEn;

    protected PagoCita() {
    }

    public static PagoCita registrar(String id, String citaId, String cuentaRecepcionId, Long montoCentavos,
                                     String moneda, MetodoPago metodo, String referencia, String idempotencyKey,
                                     OffsetDateTime ahora) {
        PagoCita pago = new PagoCita();
        pago.id = id;
        pago.citaId = citaId;
        pago.registradoPorPersonalId = cuentaRecepcionId;
        pago.montoCentavos = montoCentavos;
        pago.moneda = moneda;
        pago.metodo = metodo;
        pago.referencia = referencia;
        pago.idempotencyKey = idempotencyKey;
        pago.registradoEn = ahora;
        return pago;
    }

    public String getId() { return id; }
    public String getCitaId() { return citaId; }
    public String getRegistradoPorPersonalId() { return registradoPorPersonalId; }
    public Long getMontoCentavos() { return montoCentavos; }
    public String getMoneda() { return moneda; }
    public MetodoPago getMetodo() { return metodo; }
    public String getReferencia() { return referencia; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public OffsetDateTime getRegistradoEn() { return registradoEn; }
}
