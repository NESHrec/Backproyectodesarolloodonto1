package com.clinicaserena.staff.entity;

/** Estado derivado de la relación persistida entre una cuenta y un profesional. */
public enum EstadoVinculacionMedico {
    /** La cuenta MEDICO tiene un profesional asignado por un ADMIN. */
    VINCULADA,
    /** La cuenta MEDICO aún no tiene profesional y no puede acceder a datos clínicos. */
    PENDIENTE_VINCULACION,
    /** Las cuentas ADMIN y RECEPCION nunca se vinculan a un profesional. */
    NO_APLICA;

    public static EstadoVinculacionMedico de(CuentaPersonal cuenta) {
        if (cuenta.getRol() != RolPersonal.MEDICO) return NO_APLICA;
        return cuenta.getMedicoId() == null ? PENDIENTE_VINCULACION : VINCULADA;
    }
}
