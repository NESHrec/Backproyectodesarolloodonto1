package com.clinicaserena.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LinkPractitionerRequest(@NotBlank @Size(max = 36) String practitionerId) {
}
