package com.clinicaserena.staff.dto;

import com.clinicaserena.staff.entity.RolPersonal;

public record StaffIdentityResponse(String accountId, String email, String fullName, RolPersonal role) {
}
