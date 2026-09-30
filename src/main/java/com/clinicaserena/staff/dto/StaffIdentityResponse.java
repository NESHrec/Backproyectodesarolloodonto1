package com.clinicaserena.staff.dto;

import com.clinicaserena.staff.entity.EstadoVinculacionMedico;
import com.clinicaserena.staff.entity.RolPersonal;

public record StaffIdentityResponse(String accountId, String email, String fullName, RolPersonal role,
                                    EstadoVinculacionMedico practitionerLinkStatus, String practitionerId) {
}
