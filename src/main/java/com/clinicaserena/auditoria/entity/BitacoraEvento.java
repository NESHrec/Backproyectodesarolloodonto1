package com.clinicaserena.auditoria.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;

/** Primera versión acotada: eventos de las operaciones administrativas de esta fase. */
@Entity
@Immutable
@Table(name = "bitacora_eventos")
public class BitacoraEvento {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "actor_personal_id", length = 36, nullable = false, updatable = false)
    private String actorPersonalId;

    @Column(length = 80, nullable = false, updatable = false)
    private String accion;

    @Column(name = "entidad_tipo", length = 80, nullable = false, updatable = false)
    private String entidadTipo;

    @Column(name = "entidad_id", length = 36, nullable = false, updatable = false)
    private String entidadId;

    @Column(name = "ocurrido_en", nullable = false, updatable = false)
    private OffsetDateTime ocurridoEn;

    protected BitacoraEvento() {
    }

    public static BitacoraEvento registrar(String id, String actorPersonalId, String accion,
                                           String entidadTipo, String entidadId, OffsetDateTime ocurridoEn) {
        BitacoraEvento event = new BitacoraEvento();
        event.id = id;
        event.actorPersonalId = actorPersonalId;
        event.accion = accion;
        event.entidadTipo = entidadTipo;
        event.entidadId = entidadId;
        event.ocurridoEn = ocurridoEn;
        return event;
    }

    public String getId() { return id; }
    public String getActorPersonalId() { return actorPersonalId; }
    public String getAccion() { return accion; }
    public String getEntidadTipo() { return entidadTipo; }
    public String getEntidadId() { return entidadId; }
    public OffsetDateTime getOcurridoEn() { return ocurridoEn; }
}
