package com.clinicaserena.catalogo.controller;

import com.clinicaserena.catalogo.dto.SpecialtyDto;
import com.clinicaserena.catalogo.dto.UpsertSpecialtyRequest;
import com.clinicaserena.catalogo.service.SpecialtyAdminService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staff/especialidades")
public class SpecialtyAdminController {

    private final SpecialtyAdminService service;

    public SpecialtyAdminController(SpecialtyAdminService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<SpecialtyDto>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list());
    }

    @PostMapping
    public ResponseEntity<SpecialtyDto> create(@Valid @RequestBody UpsertSpecialtyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.create(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SpecialtyDto> update(@PathVariable String id,
                                               @Valid @RequestBody UpsertSpecialtyRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.update(id, request));
    }
}
