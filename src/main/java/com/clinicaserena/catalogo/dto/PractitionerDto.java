package com.clinicaserena.catalogo.dto;

public record PractitionerDto(
        String id,
        String fullName,
        String specialtyId,
        String specialtyName,
        String licenseNumber,
        boolean active,
        String photoUrl
) {
    public PractitionerDto(String id, String fullName, String specialtyId, String specialtyName, String licenseNumber) {
        this(id, fullName, specialtyId, specialtyName, licenseNumber, true, null);
    }
}
