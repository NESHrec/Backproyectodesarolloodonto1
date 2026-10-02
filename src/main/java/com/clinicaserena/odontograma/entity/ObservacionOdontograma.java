package com.clinicaserena.odontograma.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;

/** Observación append-only asociada a una cita propia del médico. */
@Entity
@Immutable
@Table(name = "observaciones_odontograma")
public class ObservacionOdontograma {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "paciente_id", length = 36, nullable = false, updatable = false)
    private String pacienteId;

    @Column(name = "cita_id", length = 36, nullable = false, updatable = false)
    private String citaId;

    @Column(name = "medico_id", length = 36, nullable = false, updatable = false)
    private String medicoId;

    @Column(name = "autor_personal_id", length = 36, nullable = false, updatable = false)
    private String autorPersonalId;

    @Column(name = "pieza_dental", nullable = false, updatable = false)
    private short piezaDental;

    @Column(length = 500, nullable = false, updatable = false)
    private String observacion;

    @Column(name = "registrada_en", nullable = false, updatable = false)
    private OffsetDateTime registradaEn;

    protected ObservacionOdontograma() {
    }

    public static ObservacionOdontograma registrar(String id, String pacienteId, String citaId, String medicoId,
                                                   String autorPersonalId, short piezaDental, String observacion,
                                                   OffsetDateTime registradaEn) {
        ObservacionOdontograma record = new ObservacionOdontograma();
        record.id = id;
        record.pacienteId = pacienteId;
        record.citaId = citaId;
        record.medicoId = medicoId;
        record.autorPersonalId = autorPersonalId;
        record.piezaDental = piezaDental;
        record.observacion = observacion;
        record.registradaEn = registradaEn;
        return record;
    }

    public String getId() { return id; }
    public String getPacienteId() { return pacienteId; }
    public String getCitaId() { return citaId; }
    public String getMedicoId() { return medicoId; }
    public String getAutorPersonalId() { return autorPersonalId; }
    public short getPiezaDental() { return piezaDental; }
    public String getObservacion() { return observacion; }
    public OffsetDateTime getRegistradaEn() { return registradaEn; }
}
