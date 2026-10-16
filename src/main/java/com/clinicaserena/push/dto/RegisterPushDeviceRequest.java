package com.clinicaserena.push.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Token del dispositivo. Los mensajes de error nunca repiten el valor recibido. */
public record RegisterPushDeviceRequest(
        @NotBlank @Size(min = 20, max = 4096, message = "El token debe tener entre 20 y 4096 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9:_\\-]+$", message = "El token contiene caracteres no permitidos")
        String token,
        @NotNull PushPlatform platform
) {
    @Override
    public String toString() {
        return "RegisterPushDeviceRequest[token=<oculto>, platform=" + platform + "]";
    }
}
