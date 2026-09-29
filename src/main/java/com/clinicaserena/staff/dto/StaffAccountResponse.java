package com.clinicaserena.staff.dto;

import com.clinicaserena.staff.entity.EstadoCuentaPersonal;
import com.clinicaserena.staff.entity.RolPersonal;

public record StaffAccountResponse(String accountId, String email, String fullName,
                                   RolPersonal role, EstadoCuentaPersonal status) {
}
