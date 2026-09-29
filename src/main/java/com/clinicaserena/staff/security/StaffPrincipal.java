package com.clinicaserena.staff.security;

import com.clinicaserena.staff.entity.RolPersonal;

public record StaffPrincipal(
        String accountId,
        String email,
        String fullName,
        RolPersonal role,
        String tokenHash
) {
}
