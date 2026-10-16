package com.clinicaserena.push.sender;

/**
 * Envío de una notificación a un dispositivo. Se llama siempre fuera de transacciones de base de datos.
 * Las implementaciones nunca deben registrar el token.
 */
public interface PushSender {
    PushDeliveryResult send(PushTarget target, PushMessage message);
}
