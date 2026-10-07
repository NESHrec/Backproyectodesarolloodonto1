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

    @Column(name = "actor_personal_id", length = 36, updatable = false)
    private String actorPersonalId;

    @Column(name = "actor_tipo", length = 20, nullable = false, updatable = false)
    private String actorTipo;

    @Column(name = "actor_id", length = 36, nullable = false, updatable = false)
    private String actorId;

    @Column(name = "actor_rol", length = 20, nullable = false, updatable = false)
    private String actorRol;

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

    public static BitacoraEvento registrar(String id, String actorPersonalId, String actorTipo,
                                           String actorId, String actorRol, String accion,
                                           String entidadTipo, String entidadId, OffsetDateTime ocurridoEn) {
        BitacoraEvento event = new BitacoraEvento();
        event.id = id;
        event.actorPersonalId = actorPersonalId;
        event.actorTipo = actorTipo;
        event.actorId = actorId;
        event.actorRol = actorRol;
        event.accion = accion;
        event.entidadTipo = entidadTipo;
        event.entidadId = entidadId;
        event.ocurridoEn = ocurridoEn;
        return event;
    }

    public String getId() { return id; }
    public String getActorPersonalId() { return actorPersonalId; }
    public String getActorTipo() { return actorTipo; }
    public String getActorId() { return actorId; }
    public String getActorRol() { return actorRol; }
    public String getAccion() { return accion; }
    public String getEntidadTipo() { return entidadTipo; }
    public String getEntidadId() { return entidadId; }
    public OffsetDateTime getOcurridoEn() { return ocurridoEn; }
}
