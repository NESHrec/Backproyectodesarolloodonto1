package com.clinicaserena.pagos.dto;

public record PaymentIntentStatusResponse(
        boolean active,
        PaymentIntentResponse intent
) {
    public static PaymentIntentStatusResponse absent() {
        return new PaymentIntentStatusResponse(false, null);
    }

    public static PaymentIntentStatusResponse active(PaymentIntentResponse intent) {
        return new PaymentIntentStatusResponse(true, intent);
    }
}
