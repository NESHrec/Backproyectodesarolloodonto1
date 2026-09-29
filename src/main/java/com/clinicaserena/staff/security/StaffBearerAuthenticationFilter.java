package com.clinicaserena.staff.security;

import com.clinicaserena.staff.entity.CuentaPersonal;
import com.clinicaserena.staff.entity.EstadoCuentaPersonal;
import com.clinicaserena.staff.entity.SesionPersonal;
import com.clinicaserena.staff.repository.SesionPersonalRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class StaffBearerAuthenticationFilter extends OncePerRequestFilter {

    private final ObjectProvider<SesionPersonalRepository> sessionRepository;

    public StaffBearerAuthenticationFilter(ObjectProvider<SesionPersonalRepository> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractBearerToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            SesionPersonalRepository repository = sessionRepository.getIfAvailable();
            if (repository != null) {
                repository.findByTokenHash(com.clinicaserena.auth.security.TokenHasher.sha256(token))
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

    private boolean isActive(SesionPersonal session) {
        CuentaPersonal account = session.getCuenta();
        return session.getRevocadaEn() == null
                && session.getExpiraEn().isAfter(OffsetDateTime.now(ZoneOffset.UTC))
                && account.getEstado() == EstadoCuentaPersonal.ACTIVA;
    }

    private UsernamePasswordAuthenticationToken toAuthentication(SesionPersonal session) {
        CuentaPersonal account = session.getCuenta();
        StaffPrincipal principal = new StaffPrincipal(account.getId(), account.getEmailNormalizado(),
                account.getNombreCompleto(), account.getRol(), session.getTokenHash());
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRol().name())));
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
