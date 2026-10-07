package com.clinicaserena.recepcion.dto;
import jakarta.validation.constraints.AssertTrue;
public record InitiatePatientLinkRequest(@AssertTrue(message="Debe verificar presencialmente la identidad") boolean identityVerifiedInPerson) {}
