package com.clinicaserena.recepcion.service;

import com.clinicaserena.auditoria.service.BitacoraService;
import com.clinicaserena.auth.repository.CuentaPacienteRepository;
import com.clinicaserena.auth.security.PatientPrincipal;
import com.clinicaserena.common.exception.ApiException;
import com.clinicaserena.recepcion.dto.*;
import com.clinicaserena.recepcion.entity.SolicitudVinculacionPaciente;
import com.clinicaserena.recepcion.repository.*;
import com.clinicaserena.staff.entity.RolPersonal;
import com.clinicaserena.staff.security.StaffPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class PatientLinkService {
 private final SolicitudVinculacionPacienteRepository requests; private final PacienteAdministrativoRepository admin;
 private final CuentaPacienteRepository accounts; private final JdbcTemplate jdbc; private final BitacoraService audit; private final Clock clock;
 private final SecureRandom random=new SecureRandom();
 public PatientLinkService(SolicitudVinculacionPacienteRepository requests,PacienteAdministrativoRepository admin,
 CuentaPacienteRepository accounts,JdbcTemplate jdbc,BitacoraService audit,Clock clock){this.requests=requests;this.admin=admin;this.accounts=accounts;this.jdbc=jdbc;this.audit=audit;this.clock=clock;}
 @Transactional public PatientLinkChallengeResponse initiate(StaffPrincipal actor,String patientId,InitiatePatientLinkRequest body){
  if(actor.role()!=RolPersonal.RECEPCION) throw forbidden();
  if(!body.identityVerifiedInPerson()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"IDENTITY_VERIFICATION_REQUIRED","Debe verificarse presencialmente la identidad");
  if(!admin.existsById(patientId)) throw new ApiException(HttpStatus.NOT_FOUND,"PATIENT_RECORD_NOT_FOUND","El expediente no existe");
  if(accounts.findByPacienteIdIn(List.of(patientId)).size()>0) throw new ApiException(HttpStatus.CONFLICT,"PATIENT_ALREADY_LINKED","El expediente ya esta vinculado");
  requests.findByPacienteAdministrativoIdAndEstado(patientId,"PENDIENTE").ifPresent(r->r.revocar(OffsetDateTime.now(clock)));
  requests.flush();
  String code=String.format(Locale.ROOT,"%08d",random.nextInt(100_000_000)); OffsetDateTime now=OffsetDateTime.now(clock), expiry=now.plusMinutes(10);
  var req=requests.saveAndFlush(SolicitudVinculacionPaciente.crear(UUID.randomUUID().toString(),patientId,hash(code),actor.accountId(),now,expiry));
  audit.record(actor,"PATIENT_LINK_INITIATED","PACIENTE_ADMINISTRATIVO",patientId,now);
  return new PatientLinkChallengeResponse(req.getId(),code,expiry);
 }
 @Transactional public void revoke(StaffPrincipal actor,String id){if(actor.role()!=RolPersonal.RECEPCION)throw forbidden();var r=locked(id);if("PENDIENTE".equals(r.getEstado()))r.revocar(OffsetDateTime.now(clock));audit.record(actor,"PATIENT_LINK_REVOKED","SOLICITUD_VINCULACION",id,OffsetDateTime.now(clock));}
 @Transactional(noRollbackFor = ApiException.class) public void confirm(PatientPrincipal actor,ConfirmPatientLinkRequest body){
  var r=locked(body.requestId()); OffsetDateTime now=OffsetDateTime.now(clock);
  if(!"PENDIENTE".equals(r.getEstado()))throw new ApiException(HttpStatus.CONFLICT,"PATIENT_LINK_NOT_PENDING","La solicitud ya no esta vigente");
  if(!now.isBefore(r.getExpiraEn())){r.expirar();throw new ApiException(HttpStatus.GONE,"PATIENT_LINK_EXPIRED","El codigo vencio");}
  if(!MessageDigest.isEqual(r.getCodigoHash().getBytes(StandardCharsets.US_ASCII),hash(body.code()).getBytes(StandardCharsets.US_ASCII))){r.fallo();throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"PATIENT_LINK_CODE_INVALID","El codigo no es valido");}
  accounts.findByIdForUpdate(actor.accountId()).filter(a->a.getPaciente().getId().equals(actor.patientId())).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","Autenticacion requerida"));
  if(admin.existsById(actor.patientId()))throw new ApiException(HttpStatus.CONFLICT,"PATIENT_ACCOUNT_ALREADY_LINKED","La cuenta ya tiene un expediente administrativo");
  try{int moved=jdbc.update("update pacientes_administrativos set paciente_id=? where paciente_id=?",actor.patientId(),r.getPacienteAdministrativoId());if(moved!=1)throw new ApiException(HttpStatus.CONFLICT,"PATIENT_LINK_CONFLICT","El expediente cambio durante la vinculacion");jdbc.update("delete from pacientes where id=?",r.getPacienteAdministrativoId());}
  catch(DataIntegrityViolationException e){throw new ApiException(HttpStatus.CONFLICT,"PATIENT_RECORD_HAS_DEPENDENCIES","El expediente provisional tiene datos asociados y requiere revision administrativa");}
  r.confirmar(actor.accountId(),now);audit.recordPatient(actor,"PATIENT_LINK_CONFIRMED","PACIENTE",actor.patientId(),now);
 }
 private SolicitudVinculacionPaciente locked(String id){return requests.findByIdForUpdate(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PATIENT_LINK_NOT_FOUND","La solicitud no existe"));}
 private ApiException forbidden(){return new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","No tienes permiso para esta operacion");}
 private static String hash(String value){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(b);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
