package com.clinicaserena.catalogo.dto;

public record PractitionerDto(
        String id,
        String fullName,
        String specialtyId,
        String specialtyName,
        String licenseNumber
) {
}
