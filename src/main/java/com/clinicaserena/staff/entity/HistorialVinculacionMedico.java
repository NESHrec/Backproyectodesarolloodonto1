package com.clinicaserena.staff.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;

/** Registro de solo inserción de cada asignación o corrección administrativa. */
@Entity
@Immutable
@Table(name = "historial_vinculacion_medico")
public class HistorialVinculacionMedico {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "cuenta_personal_id", length = 36, nullable = false, updatable = false)
    private String cuentaPersonalId;

    @Column(name = "medico_anterior_id", length = 36, updatable = false)
    private String medicoAnteriorId;

    @Column(name = "medico_nuevo_id", length = 36, updatable = false)
    private String medicoNuevoId;

    @Column(name = "realizado_por", length = 36, nullable = false, updatable = false)
    private String realizadoPor;

    @Column(name = "realizado_en", nullable = false, updatable = false)
    private OffsetDateTime realizadoEn;

    protected HistorialVinculacionMedico() {
    }

    public static HistorialVinculacionMedico registrar(String id, String cuentaPersonalId, String medicoAnteriorId,
                                                       String medicoNuevoId, String realizadoPor,
                                                       OffsetDateTime realizadoEn) {
        HistorialVinculacionMedico registro = new HistorialVinculacionMedico();
        registro.id = id;
        registro.cuentaPersonalId = cuentaPersonalId;
        registro.medicoAnteriorId = medicoAnteriorId;
        registro.medicoNuevoId = medicoNuevoId;
        registro.realizadoPor = realizadoPor;
        registro.realizadoEn = realizadoEn;
        return registro;
    }
}
