package com.clinicaserena.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepción base de negocio. Los módulos de fases siguientes (catalogo, citas, auth)
 * deben lanzar esta excepción (o una subclase) en lugar de exponer excepciones internas.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
