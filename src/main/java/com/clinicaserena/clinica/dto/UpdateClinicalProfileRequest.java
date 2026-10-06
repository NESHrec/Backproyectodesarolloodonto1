package com.clinicaserena.clinica.dto;
import jakarta.validation.constraints.Size;
public record UpdateClinicalProfileRequest(@Size(max=2000) String allergies,
                                           @Size(max=2000) String relevantConditions,
                                           @Size(max=2000) String currentMedications,
                                           @Size(max=2000) String dentalHistory) {}
