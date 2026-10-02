package com.clinicaserena.clinica.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Medicamento indicado en la receta de una atención. */
@Entity
@Immutable
@Table(name = "receta_items")
public class RecetaItem {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "atencion_id", length = 36, nullable = false, updatable = false)
    private String atencionId;

    @Column(nullable = false, updatable = false)
    private short orden;

    @Column(length = 160, nullable = false, updatable = false)
    private String medicamento;

    @Column(length = 80, nullable = false, updatable = false)
    private String dosis;

    @Column(length = 80, nullable = false, updatable = false)
    private String frecuencia;

    @Column(length = 80, nullable = false, updatable = false)
    private String duracion;

    @Column(length = 500, updatable = false)
    private String indicaciones;

    protected RecetaItem() {
    }

    public static RecetaItem registrar(String id, String atencionId, short orden, String medicamento, String dosis,
                                       String frecuencia, String duracion, String indicaciones) {
        RecetaItem item = new RecetaItem();
        item.id = id;
        item.atencionId = atencionId;
        item.orden = orden;
        item.medicamento = medicamento;
        item.dosis = dosis;
        item.frecuencia = frecuencia;
        item.duracion = duracion;
        item.indicaciones = indicaciones;
        return item;
    }

    public String getId() { return id; }
    public String getAtencionId() { return atencionId; }
    public short getOrden() { return orden; }
    public String getMedicamento() { return medicamento; }
    public String getDosis() { return dosis; }
    public String getFrecuencia() { return frecuencia; }
    public String getDuracion() { return duracion; }
    public String getIndicaciones() { return indicaciones; }
}
