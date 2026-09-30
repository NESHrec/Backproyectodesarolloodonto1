package com.clinicaserena.pagos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "intenciones_pago_recepcion")
public class IntencionPagoRecepcion {

    @Id
    @Column(name = "cuenta_recepcion_id", length = 36, nullable = false, updatable = false)
    private String cuentaRecepcionId;

    @Column(name = "cita_id", length = 36, nullable = false, updatable = false)
    private String citaId;

    @Column(name = "monto_centavos", nullable = false, updatable = false)
    private Long montoCentavos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private MetodoPago metodo;

    @Column(length = 120, updatable = false)
    private String referencia;

    @Column(name = "idempotency_key", length = 80, nullable = false, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoIntencionPago estado;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private OffsetDateTime creadaEn;

    @Column(name = "completada_en")
    private OffsetDateTime completadaEn;

    protected IntencionPagoRecepcion() {
    }

    public static IntencionPagoRecepcion preparar(String cuentaId, String citaId, Long monto,
                                                   MetodoPago metodo, String referencia, String clave,
                                                   OffsetDateTime ahora) {
        IntencionPagoRecepcion intent = new IntencionPagoRecepcion();
        intent.cuentaRecepcionId = cuentaId;
        intent.citaId = citaId;
        intent.montoCentavos = monto;
        intent.metodo = metodo;
        intent.referencia = referencia;
        intent.idempotencyKey = clave;
        intent.estado = EstadoIntencionPago.PREPARADA;
        intent.creadaEn = ahora;
        return intent;
    }

    public void completar(OffsetDateTime ahora) {
        estado = EstadoIntencionPago.COMPLETADA;
        completadaEn = ahora;
    }

    public String getCuentaRecepcionId() { return cuentaRecepcionId; }
    public String getCitaId() { return citaId; }
    public Long getMontoCentavos() { return montoCentavos; }
    public MetodoPago getMetodo() { return metodo; }
    public String getReferencia() { return referencia; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public EstadoIntencionPago getEstado() { return estado; }
    public OffsetDateTime getCreadaEn() { return creadaEn; }
    public OffsetDateTime getCompletadaEn() { return completadaEn; }
}
