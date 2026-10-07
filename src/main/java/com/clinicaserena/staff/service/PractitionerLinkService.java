package com.clinicaserena.staff.service;

import com.clinicaserena.catalogo.entity.Medico;
import com.clinicaserena.catalogo.repository.MedicoRepository;
import com.clinicaserena.common.exception.ApiException;
import com.clinicaserena.staff.dto.StaffAccountResponse;
import com.clinicaserena.staff.entity.CuentaPersonal;
import com.clinicaserena.staff.entity.EstadoCuentaPersonal;
import com.clinicaserena.staff.entity.HistorialVinculacionMedico;
import com.clinicaserena.staff.entity.RolPersonal;
import com.clinicaserena.staff.repository.CuentaPersonalRepository;
import com.clinicaserena.staff.repository.HistorialVinculacionMedicoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import com.clinicaserena.auditoria.service.BitacoraService;
import com.clinicaserena.staff.security.StaffPrincipal;

/**
 * Asignación administrativa entre cuentas MEDICO y profesionales del catálogo.
 * El identificador del ADMIN siempre proviene del principal autenticado.
 */
@Service
public class PractitionerLinkService {

    private final CuentaPersonalRepository accountRepository;
    private final MedicoRepository medicoRepository;
    private final HistorialVinculacionMedicoRepository historyRepository;
    private final BitacoraService audit;

    public PractitionerLinkService(CuentaPersonalRepository accountRepository, MedicoRepository medicoRepository,
                                   HistorialVinculacionMedicoRepository historyRepository, BitacoraService audit) {
        this.accountRepository = accountRepository;
        this.medicoRepository = medicoRepository;
        this.historyRepository = historyRepository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<StaffAccountResponse> list(RolPersonal role) {
        List<CuentaPersonal> accounts = role == null
                ? accountRepository.findAllByOrderByNombreCompletoAsc()
                : accountRepository.findByRolOrderByNombreCompletoAsc(role);
        Map<String, String> names = medicoRepository.findAllById(accounts.stream()
                        .map(CuentaPersonal::getMedicoId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Medico::getId, Medico::getNombreCompleto));
        return accounts.stream().map(account -> StaffAccountResponse.of(account, names.get(account.getMedicoId())))
                .toList();
    }

    @Transactional
    public StaffAccountResponse link(StaffPrincipal admin, String accountId, String practitionerId) {
        CuentaPersonal account = lockMedicalAccount(accountId);
        Medico medico = medicoRepository.findByIdForUpdate(practitionerId.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRACTITIONER_NOT_FOUND",
                        "El profesional no existe"));
        if (medico.getId().equals(account.getMedicoId())) {
            return StaffAccountResponse.of(account, medico.getNombreCompleto());
        }
        boolean takenByOtherActiveAccount = accountRepository
                .findByMedicoIdAndEstado(medico.getId(), EstadoCuentaPersonal.ACTIVA).stream()
                .anyMatch(other -> !other.getId().equals(account.getId()));
        if (takenByOtherActiveAccount) throw alreadyLinked();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String previous = account.getMedicoId();
        account.vincularMedico(medico.getId(), admin.accountId(), now);
        try {
            accountRepository.saveAndFlush(account);
            historyRepository.saveAndFlush(HistorialVinculacionMedico.registrar(UUID.randomUUID().toString(),
                    account.getId(), previous, medico.getId(), admin.accountId(), now));
            audit.record(admin,"STAFF_PRACTITIONER_LINKED","CUENTA_PERSONAL",accountId,now);
        } catch (DataIntegrityViolationException collision) {
            throw alreadyLinked();
        }
        return StaffAccountResponse.of(account, medico.getNombreCompleto());
    }

    @Transactional
    public StaffAccountResponse unlink(StaffPrincipal admin, String accountId) {
        CuentaPersonal account = lockMedicalAccount(accountId);
        String previous = account.getMedicoId();
        if (previous == null) {
            throw new ApiException(HttpStatus.CONFLICT, "PRACTITIONER_NOT_LINKED",
                    "La cuenta no tiene un profesional asignado");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        account.desvincularMedico(now);
        accountRepository.saveAndFlush(account);
        historyRepository.saveAndFlush(HistorialVinculacionMedico.registrar(UUID.randomUUID().toString(),
                account.getId(), previous, null, admin.accountId(), now));
        audit.record(admin,"STAFF_PRACTITIONER_UNLINKED","CUENTA_PERSONAL",accountId,now);
        return StaffAccountResponse.of(account, null);
    }

    private CuentaPersonal lockMedicalAccount(String accountId) {
        CuentaPersonal account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STAFF_ACCOUNT_NOT_FOUND",
                        "La cuenta de personal no existe"));
        if (account.getRol() != RolPersonal.MEDICO) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STAFF_ACCOUNT_NOT_MEDICAL",
                    "Solo una cuenta MEDICO puede vincularse a un profesional");
        }
        return account;
    }

    private ApiException alreadyLinked() {
        return new ApiException(HttpStatus.CONFLICT, "PRACTITIONER_ALREADY_LINKED",
                "El profesional ya está asignado a otra cuenta activa");
    }
}
