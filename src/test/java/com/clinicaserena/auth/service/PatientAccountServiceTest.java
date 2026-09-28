package com.clinicaserena.auth.service;

import com.clinicaserena.auth.dto.EmailRequest;
import com.clinicaserena.auth.entity.*;
import com.clinicaserena.auth.repository.*;
import com.clinicaserena.auth.security.IdentityRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PatientAccountServiceTest {
    @Test void smtpCaidoNoDistingueCuentaExistenteDeInexistente() {
        PacienteRepository patients=mock(PacienteRepository.class);CuentaPacienteRepository accounts=mock(CuentaPacienteRepository.class);
        TokenCuentaPacienteRepository tokens=mock(TokenCuentaPacienteRepository.class);SesionPacienteRepository sessions=mock(SesionPacienteRepository.class);
        PasswordEncoder encoder=mock(PasswordEncoder.class);AccountMailService mail=mock(AccountMailService.class);
        TransactionTemplate tx=mock(TransactionTemplate.class);TransactionStatus status=mock(TransactionStatus.class);
        when(tx.execute(any())).thenAnswer(inv->{TransactionCallback<?> callback=inv.getArgument(0);return callback.doInTransaction(status);});
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);CuentaPaciente account=CuentaPaciente.crear("a",Paciente.crear("p",now),"exists@example.test","hash","Paciente",now);account.verificarEmail(now);
        when(accounts.findByEmailNormalizado("exists@example.test")).thenReturn(Optional.of(account));
        when(accounts.findByIdForUpdate("a")).thenReturn(Optional.of(account));
        when(accounts.findByEmailNormalizado("missing@example.test")).thenReturn(Optional.empty());
        doThrow(new MailSendException("smtp down")).when(mail).recovery(eq("exists@example.test"),anyString());
        PatientAccountService service=new PatientAccountService(patients,accounts,tokens,sessions,encoder,mail,new IdentityRateLimiter(3,900,100),tx,86400,1800);
        var existing=service.recover(new EmailRequest("exists@example.test"));var missing=service.recover(new EmailRequest("missing@example.test"));
        assertThat(existing).isEqualTo(missing);verify(tokens).invalidateActive(eq("a"),eq(TipoTokenCuenta.RECUPERACION_PASSWORD),any());
    }
}
