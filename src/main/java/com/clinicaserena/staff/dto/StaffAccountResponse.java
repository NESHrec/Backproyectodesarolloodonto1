package com.clinicaserena.staff.dto;

import com.clinicaserena.staff.entity.CuentaPersonal;
import com.clinicaserena.staff.entity.EstadoCuentaPersonal;
import com.clinicaserena.staff.entity.EstadoVinculacionMedico;
import com.clinicaserena.staff.entity.RolPersonal;

import java.time.OffsetDateTime;

public record StaffAccountResponse(String accountId, String email, String fullName,
                                   RolPersonal role, EstadoCuentaPersonal status,
                                   EstadoVinculacionMedico practitionerLinkStatus,
                                   String practitionerId, String practitionerName,
                                   OffsetDateTime practitionerLinkedAt) {

    public static StaffAccountResponse of(CuentaPersonal account, String practitionerName) {
        return new StaffAccountResponse(account.getId(), account.getEmailNormalizado(), account.getNombreCompleto(),
                account.getRol(), account.getEstado(), EstadoVinculacionMedico.de(account),
                account.getMedicoId(), practitionerName, account.getMedicoVinculadoEn());
    }
}
