package com.clinicaserena.pagos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "cargos_citas_auditoria")
public class CargoCitaAuditoria {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "cita_id", length = 36, nullable = false, updatable = false)
    private String citaId;

    @Column(name = "monto_anterior_centavos", updatable = false)
    private Long montoAnteriorCentavos;

    @Column(name = "monto_nuevo_centavos", nullable = false, updatable = false)
    private Long montoNuevoCentavos;

    @Column(name = "moneda_anterior", length = 3, updatable = false)
    private String monedaAnterior;

    @Column(name = "moneda_nueva", length = 3, nullable = false, updatable = false)
    private String monedaNueva;

    @Column(name = "fijado_por_personal_id", length = 36, nullable = false, updatable = false)
    private String fijadoPorPersonalId;

    @Column(name = "fijado_en", nullable = false, updatable = false)
    private OffsetDateTime fijadoEn;

    protected CargoCitaAuditoria() {
    }

    public static CargoCitaAuditoria registrar(String id, String citaId, Long montoAnterior, Long montoNuevo,
                                               String monedaAnterior, String monedaNueva, String cuentaRecepcionId,
                                               OffsetDateTime ahora) {
        CargoCitaAuditoria auditoria = new CargoCitaAuditoria();
        auditoria.id = id;
        auditoria.citaId = citaId;
        auditoria.montoAnteriorCentavos = montoAnterior;
        auditoria.montoNuevoCentavos = montoNuevo;
        auditoria.monedaAnterior = monedaAnterior;
        auditoria.monedaNueva = monedaNueva;
        auditoria.fijadoPorPersonalId = cuentaRecepcionId;
        auditoria.fijadoEn = ahora;
        return auditoria;
    }
}
