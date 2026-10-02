package com.clinicaserena.common.time;

import java.time.ZoneId;

/** Zona de negocio para interpretar y presentar horarios clínicos. */
public final class BusinessTime {

    public static final ZoneId ZONE = ZoneId.of("America/Guatemala");

    private BusinessTime() {
    }
}
