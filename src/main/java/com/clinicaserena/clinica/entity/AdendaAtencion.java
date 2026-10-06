package com.clinicaserena.clinica.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import java.time.OffsetDateTime;

@Entity
@Immutable
@Table(name = "adendas_atencion")
public class AdendaAtencion {
    @Id @Column(length = 36, updatable = false) private String id;
    @Column(name = "atencion_id", length = 36, nullable = false, updatable = false) private String atencionId;
    @Column(length = 2000, nullable = false, updatable = false) private String texto;
    @Column(length = 500, nullable = false, updatable = false) private String motivo;
    @Column(name = "autor_personal_id", length = 36, nullable = false, updatable = false) private String autorPersonalId;
    @Column(name = "registrada_en", nullable = false, updatable = false) private OffsetDateTime registradaEn;
    protected AdendaAtencion() {}
    public static AdendaAtencion registrar(String id, String atencionId, String texto, String motivo, String autor, OffsetDateTime fecha) {
        AdendaAtencion value = new AdendaAtencion(); value.id=id; value.atencionId=atencionId; value.texto=texto;
        value.motivo=motivo; value.autorPersonalId=autor; value.registradaEn=fecha; return value;
    }
    public String getId(){return id;} public String getAtencionId(){return atencionId;} public String getTexto(){return texto;}
    public String getMotivo(){return motivo;} public String getAutorPersonalId(){return autorPersonalId;}
    public OffsetDateTime getRegistradaEn(){return registradaEn;}
}
