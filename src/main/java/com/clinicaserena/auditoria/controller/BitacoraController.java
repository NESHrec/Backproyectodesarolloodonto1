package com.clinicaserena.auditoria.controller;

import com.clinicaserena.auditoria.dto.AuditEventPageResponse;
import com.clinicaserena.auditoria.service.BitacoraService;
import com.clinicaserena.staff.security.StaffPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff/audit-events")
public class BitacoraController {

    private final BitacoraService service;

    public BitacoraController(BitacoraService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<AuditEventPageResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.list((StaffPrincipal) authentication.getPrincipal(), page, limit));
    }
}
