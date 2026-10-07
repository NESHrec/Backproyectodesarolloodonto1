package com.clinicaserena.catalogo.controller;

import com.clinicaserena.catalogo.dto.ScheduleBlockDto;
import com.clinicaserena.catalogo.dto.ScheduleBlockRequest;
import com.clinicaserena.catalogo.service.MedicalScheduleService;
import com.clinicaserena.staff.security.StaffPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staff/medico/horarios")
public class MedicalScheduleController {

    private final MedicalScheduleService service;

    public MedicalScheduleController(MedicalScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ScheduleBlockDto>> list(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.listOwn(principal(authentication)));
    }

    @PostMapping
    public ResponseEntity<ScheduleBlockDto> add(@Valid @RequestBody ScheduleBlockRequest request,
                                                Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.addOwn(principal(authentication), request));
    }

    @PatchMapping("/{blockId}")
    public ResponseEntity<ScheduleBlockDto> update(@PathVariable String blockId,
                                                   @Valid @RequestBody ScheduleBlockRequest request,
                                                   Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.updateOwn(principal(authentication), blockId, request));
    }

    @DeleteMapping("/{blockId}")
    public ResponseEntity<Void> retire(@PathVariable String blockId, Authentication authentication) {
        service.retireOwn(principal(authentication), blockId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static StaffPrincipal principal(Authentication authentication) {
        return (StaffPrincipal) authentication.getPrincipal();
    }
}
