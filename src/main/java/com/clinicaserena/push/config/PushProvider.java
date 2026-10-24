package com.clinicaserena.push.config;

/** Proveedor de notificaciones. NONE: apagado. SIMULATED: solo dev/test. FIREBASE: Firebase Cloud Messaging. */
public enum PushProvider {
    NONE,
    SIMULATED,
    FIREBASE
}
