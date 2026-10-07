package com.clinicaserena.recepcion.dto;
import java.time.OffsetDateTime;
public record PatientLinkChallengeResponse(String requestId,String oneTimeCode,OffsetDateTime expiresAt) {}
