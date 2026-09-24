package com.clinicaserena.common.response;

import java.util.List;

/**
 * Formato estandar de error de la API. No debe exponer detalles internos
 * (stack traces, mensajes de excepciones de infraestructura, SQL, etc.).
 */
public record ApiError(
        int status,
        String code,
        String message,
        List<FieldErrorItem> fieldErrors
) {

    public record FieldErrorItem(String field, String message) {
    }

    public static ApiError of(int status, String code, String message) {
        return new ApiError(status, code, message, List.of());
    }

    public static ApiError of(int status, String code, String message, List<FieldErrorItem> fieldErrors) {
        return new ApiError(status, code, message, fieldErrors);
    }
}
