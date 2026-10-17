package com.clinicaserena.push.sender;

/**
 * Resultado por dispositivo. SIMULATED nunca equivale a una entrega: el recordatorio queda "SIMULADO",
 * no "ENVIADO". INVALID_TOKEN hace que se retire el dispositivo.
 */
public record PushDeliveryResult(Outcome outcome, String errorCode) {

    public enum Outcome { SIMULATED, DELIVERED, INVALID_TOKEN, FAILED }

    public static PushDeliveryResult simulated() { return new PushDeliveryResult(Outcome.SIMULATED, null); }
    public static PushDeliveryResult delivered() { return new PushDeliveryResult(Outcome.DELIVERED, null); }
    public static PushDeliveryResult invalidToken() { return new PushDeliveryResult(Outcome.INVALID_TOKEN, "TOKEN_INVALIDO"); }
    public static PushDeliveryResult failed(String errorCode) { return new PushDeliveryResult(Outcome.FAILED, errorCode); }

    public boolean succeeded() {
        return outcome == Outcome.SIMULATED || outcome == Outcome.DELIVERED;
    }
}
