package com.clinicaserena.auth.service;

import com.clinicaserena.auth.dto.*;
import com.clinicaserena.auth.entity.*;
import com.clinicaserena.auth.repository.*;
import com.clinicaserena.auth.security.*;
import com.clinicaserena.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

@Service
public class PatientAccountService {
    public static final String GENERIC="Si la solicitud es válida, recibirás un correo con los pasos siguientes.";
    private final PacienteRepository patients; private final CuentaPacienteRepository accounts;
    private final TokenCuentaPacienteRepository tokens; private final SesionPacienteRepository sessions;
    private final PasswordEncoder encoder; private final AccountMailService mail; private final IdentityRateLimiter limiter;
    private final TransactionTemplate transactionTemplate;
    private final long verificationTtl; private final long recoveryTtl; private static final SecureRandom RANDOM=new SecureRandom();
    public PatientAccountService(PacienteRepository patients, CuentaPacienteRepository accounts,
            TokenCuentaPacienteRepository tokens, SesionPacienteRepository sessions, PasswordEncoder encoder,
            AccountMailService mail, IdentityRateLimiter limiter,
            TransactionTemplate transactionTemplate,
            @Value("${clinica.auth.verification-ttl-seconds:86400}") long verificationTtl,
            @Value("${clinica.auth.recovery-ttl-seconds:1800}") long recoveryTtl) {
        this.patients=patients;this.accounts=accounts;this.tokens=tokens;this.sessions=sessions;this.encoder=encoder;
        this.mail=mail;this.limiter=limiter;this.transactionTemplate=transactionTemplate;this.verificationTtl=verificationTtl;this.recoveryTtl=recoveryTtl;
    }
    public GenericMessageResponse register(RegisterRequest request) {
        String email=normalize(request.email());
        if(!limiter.acquire("register",email)) return new GenericMessageResponse(GENERIC);
        if(accounts.findByEmailNormalizado(email).isPresent()) return new GenericMessageResponse(GENERIC);
        try {
            String raw=transactionTemplate.execute(status->{ OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
                Paciente patient=Paciente.crear(UUID.randomUUID().toString(),now);
                CuentaPaciente account=CuentaPaciente.crear(UUID.randomUUID().toString(),patient,email,encoder.encode(request.password()),request.nombre().trim(),now);
                patients.save(patient);accounts.saveAndFlush(account);String token=randomToken();
                tokens.save(TokenCuentaPaciente.crear(UUID.randomUUID().toString(),account,TipoTokenCuenta.VERIFICACION_EMAIL,
                        TokenHasher.sha256(token),now.plusSeconds(verificationTtl),now));return token;});
            if(raw!=null) safelySendVerification(email,raw);
        } catch(DataIntegrityViolationException collision) { /* generic response: concurrent duplicate */ }
        return new GenericMessageResponse(GENERIC);
    }
    public GenericMessageResponse resendVerification(EmailRequest request) {
        String email=normalize(request.email()); if(!limiter.acquire("resend-verification",email)) return generic();
        accounts.findByEmailNormalizado(email).filter(a->a.getEmailVerificadoEn()==null).ifPresent(account->{
            String raw=transactionTemplate.execute(status->issue(account,TipoTokenCuenta.VERIFICACION_EMAIL,verificationTtl));
            if(raw!=null) safelySendVerification(email,raw);
        }); return generic();
    }
    @Transactional
    public void verify(TokenRequest request) { consume(request.token(),TipoTokenCuenta.VERIFICACION_EMAIL).getCuenta()
            .verificarEmail(OffsetDateTime.now(ZoneOffset.UTC)); }
    @Transactional
    public GenericMessageResponse recover(EmailRequest request) {
        String email=normalize(request.email()); if(!limiter.acquire("recover",email)) return new GenericMessageResponse(GENERIC);
        accounts.findByEmailNormalizado(email).filter(a->a.getEmailVerificadoEn()!=null).ifPresent(account->{
            String raw=transactionTemplate.execute(status->issue(account,TipoTokenCuenta.RECUPERACION_PASSWORD,recoveryTtl));
            if(raw!=null) try { mail.recovery(email,raw); } catch(MailException ignored) { /* generic response */ }
        }); return new GenericMessageResponse(GENERIC);
    }
    @Transactional
    public void reset(ResetPasswordRequest request) { TokenCuentaPaciente token=consume(request.token(),TipoTokenCuenta.RECUPERACION_PASSWORD);
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC); token.getCuenta().cambiarPassword(encoder.encode(request.password()),now);
        sessions.revokeAllByCuentaId(token.getCuenta().getId(),now); }
    private TokenCuentaPaciente consume(String raw,TipoTokenCuenta type) { OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        TokenCuentaPaciente token=tokens.findByTokenHashAndTipo(TokenHasher.sha256(raw),type).orElseThrow(this::invalidToken);
        if(token.getUsadoEn()!=null || !token.getExpiraEn().isAfter(now)) throw invalidToken(); token.usar(now); return token; }
    private ApiException invalidToken(){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_OR_EXPIRED_TOKEN","El enlace es inválido, venció o ya fue utilizado");}
    private String issue(CuentaPaciente account,TipoTokenCuenta type,long ttl) {OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        CuentaPaciente locked=accounts.findByIdForUpdate(account.getId()).orElseThrow();
        tokens.invalidateActive(locked.getId(),type,now);String raw=randomToken();tokens.saveAndFlush(TokenCuentaPaciente.crear(
                UUID.randomUUID().toString(),locked,type,TokenHasher.sha256(raw),now.plusSeconds(ttl),now));return raw;}
    private void safelySendVerification(String email,String raw){try{mail.verification(email,raw);}catch(MailException ignored){/* resend remains possible */}}
    private GenericMessageResponse generic(){return new GenericMessageResponse(GENERIC);}
    public static String normalize(String value){return value.trim().toLowerCase(Locale.ROOT);}
    private String randomToken(){byte[] b=new byte[32];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
}
