package com.clinicaserena.push.sender;

import java.util.Map;

/** Notificación sin datos clínicos: título, cuerpo y datos opacos para abrir la pantalla correcta. */
public record PushMessage(String title, String body, Map<String, String> data) {
    public PushMessage {
        data = Map.copyOf(data);
    }
}
