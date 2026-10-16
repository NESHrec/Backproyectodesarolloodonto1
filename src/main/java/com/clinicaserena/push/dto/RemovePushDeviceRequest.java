package com.clinicaserena.push.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RemovePushDeviceRequest(
        @NotBlank @Size(min = 20, max = 4096, message = "El token debe tener entre 20 y 4096 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9:_\\-]+$", message = "El token contiene caracteres no permitidos")
        String token
) {
    @Override
    public String toString() {
        return "RemovePushDeviceRequest[token=<oculto>]";
    }
}
