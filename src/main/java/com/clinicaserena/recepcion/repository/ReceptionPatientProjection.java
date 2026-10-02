package com.clinicaserena.recepcion.repository;

import java.time.Instant;

/** Una fila por paciente_id estable, aun cuando el origen sea histórico. */
public interface ReceptionPatientProjection {
    String getPatientId();
    String getFullName();
    String getPhone();
    String getEmail();
    String getRecordType();
    boolean getPatientAccountLinked();
    Instant getCreatedAt();
}
