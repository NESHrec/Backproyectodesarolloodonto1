package com.clinicaserena.push.sender;

/** Destino de un envío. El token es privado: {@link #toString()} lo oculta para que no llegue a los logs. */
public record PushTarget(String deviceId, String token) {
    @Override
    public String toString() {
        return "PushTarget[deviceId=" + deviceId + ", token=<oculto>]";
    }
}
