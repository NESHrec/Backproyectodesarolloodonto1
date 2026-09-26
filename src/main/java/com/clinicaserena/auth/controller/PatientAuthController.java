package com.clinicaserena.auth.controller;

import com.clinicaserena.auth.dto.LoginRequest;
import com.clinicaserena.auth.dto.LoginResponse;
import com.clinicaserena.auth.dto.PatientIdentityResponse;
import com.clinicaserena.auth.security.PatientPrincipal;
import com.clinicaserena.auth.service.PatientAuthService;
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

    public PatientAuthController(PatientAuthService authService) {
        this.authService = authService;
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
