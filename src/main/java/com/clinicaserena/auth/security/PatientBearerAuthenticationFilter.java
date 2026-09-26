package com.clinicaserena.auth.security;

import com.clinicaserena.auth.entity.CuentaPaciente;
import com.clinicaserena.auth.entity.EstadoCuentaPaciente;
import com.clinicaserena.auth.entity.EstadoPaciente;
import com.clinicaserena.auth.entity.SesionPaciente;
import com.clinicaserena.auth.repository.SesionPacienteRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class PatientBearerAuthenticationFilter extends OncePerRequestFilter {

    private final ObjectProvider<SesionPacienteRepository> sesionRepository;

    public PatientBearerAuthenticationFilter(ObjectProvider<SesionPacienteRepository> sesionRepository) {
        this.sesionRepository = sesionRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = extractBearerToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            SesionPacienteRepository repository = sesionRepository.getIfAvailable();
            if (repository != null) {
                repository.findByTokenHash(TokenHasher.sha256(token))
                        .filter(this::isActive)
                        .map(this::toAuthentication)
                        .ifPresent(authentication -> {
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        });
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isActive(SesionPaciente session) {
        CuentaPaciente account = session.getCuenta();
        return session.getRevocadaEn() == null
                && session.getExpiraEn().isAfter(OffsetDateTime.now(ZoneOffset.UTC))
                && account.getEstado() == EstadoCuentaPaciente.ACTIVA
                && account.getPaciente().getEstado() == EstadoPaciente.ACTIVO;
    }

    private UsernamePasswordAuthenticationToken toAuthentication(SesionPaciente session) {
        CuentaPaciente account = session.getCuenta();
        PatientPrincipal principal = new PatientPrincipal(
                account.getId(),
                account.getPaciente().getId(),
                account.getEmailNormalizado(),
                account.getEstado().name(),
                session.getTokenHash()
        );
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"))
        );
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.length() <= 7 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
