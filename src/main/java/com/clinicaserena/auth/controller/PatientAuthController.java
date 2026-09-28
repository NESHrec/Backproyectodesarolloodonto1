package com.clinicaserena.auth.controller;

import com.clinicaserena.auth.dto.LoginRequest;
import com.clinicaserena.auth.dto.LoginResponse;
import com.clinicaserena.auth.dto.PatientIdentityResponse;
import com.clinicaserena.auth.security.PatientPrincipal;
import com.clinicaserena.auth.service.PatientAuthService;
import com.clinicaserena.auth.service.PatientAccountService;
import com.clinicaserena.auth.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class PatientAuthController {

    private final PatientAuthService authService;
    private final PatientAccountService accountService;

    public PatientAuthController(PatientAuthService authService, PatientAccountService accountService) {
        this.authService = authService;
        this.accountService = accountService;
    }

    @PostMapping("/register")
    public ResponseEntity<GenericMessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(accountService.register(request));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verify(@Valid @RequestBody TokenRequest request) {
        accountService.verify(request); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<GenericMessageResponse> resendVerification(@Valid @RequestBody EmailRequest request) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(accountService.resendVerification(request));
    }

    @PostMapping("/password-recovery")
    public ResponseEntity<GenericMessageResponse> recover(@Valid @RequestBody EmailRequest request) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(accountService.recover(request));
    }

    @PostMapping("/password-reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        accountService.reset(request); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authService.login(request, httpRequest.getRemoteAddr()));
    }

    @GetMapping("/me")
    public ResponseEntity<PatientIdentityResponse> me(Authentication authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authService.me((PatientPrincipal) authentication.getPrincipal()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        authService.logout((PatientPrincipal) authentication.getPrincipal());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
