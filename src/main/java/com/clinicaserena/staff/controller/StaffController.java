package com.clinicaserena.staff.controller;

import com.clinicaserena.auth.dto.LoginResponse;
import com.clinicaserena.citas.entity.EstadoCita;
import com.clinicaserena.staff.dto.CreateStaffAccountRequest;
import com.clinicaserena.staff.dto.ReceptionAppointmentResponse;
import com.clinicaserena.staff.dto.StaffAccountResponse;
import com.clinicaserena.staff.dto.StaffIdentityResponse;
import com.clinicaserena.staff.dto.StaffLoginRequest;
import com.clinicaserena.staff.security.StaffPrincipal;
import com.clinicaserena.staff.service.StaffAgendaService;
import com.clinicaserena.staff.service.StaffAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/staff")
public class StaffController {

    private final StaffAuthService authService;
    private final StaffAgendaService agendaService;

    public StaffController(StaffAuthService authService, StaffAgendaService agendaService) {
        this.authService = authService;
        this.agendaService = agendaService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody StaffLoginRequest request,
                                               HttpServletRequest httpRequest) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authService.login(request, httpRequest.getRemoteAddr()));
    }

    @GetMapping("/auth/me")
    public ResponseEntity<StaffIdentityResponse> me(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authService.me((StaffPrincipal) authentication.getPrincipal()));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        authService.logout((StaffPrincipal) authentication.getPrincipal());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/accounts")
    public ResponseEntity<StaffAccountResponse> createAccount(@Valid @RequestBody CreateStaffAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(authService.createAccount(request));
    }

    @GetMapping("/agenda")
    public ResponseEntity<List<ReceptionAppointmentResponse>> agenda(
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) EstadoCita status,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(agendaService.list(from, to, status, limit));
    }

    @PostMapping("/agenda/{appointmentId}/arrival")
    public ResponseEntity<ReceptionAppointmentResponse> registerArrival(
            @PathVariable String appointmentId, Authentication authentication) {
        StaffPrincipal principal = (StaffPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(agendaService.registerArrival(appointmentId, principal.accountId()));
    }
}
