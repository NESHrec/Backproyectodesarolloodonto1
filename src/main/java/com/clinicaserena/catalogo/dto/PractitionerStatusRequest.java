package com.clinicaserena.catalogo.dto;

import jakarta.validation.constraints.NotNull;

public record PractitionerStatusRequest(@NotNull Boolean active) {}
