package com.clinicaserena.catalogo.controller;

import com.clinicaserena.catalogo.dto.PractitionerDto;
import com.clinicaserena.catalogo.dto.PractitionerStatusRequest;
import com.clinicaserena.catalogo.dto.UpsertPractitionerRequest;
import com.clinicaserena.catalogo.service.PractitionerAdminService;
import com.clinicaserena.staff.security.StaffPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staff/medicos")
public class PractitionerAdminController {
    private final PractitionerAdminService service;
    public PractitionerAdminController(PractitionerAdminService service) { this.service = service; }
    @GetMapping public ResponseEntity<List<PractitionerDto>> list() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list()); }
    @PostMapping public ResponseEntity<PractitionerDto> create(@Valid @RequestBody UpsertPractitionerRequest request, Authentication auth) { return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.create((StaffPrincipal) auth.getPrincipal(), request)); }
    @PatchMapping("/{id}") public ResponseEntity<PractitionerDto> update(@PathVariable String id, @Valid @RequestBody UpsertPractitionerRequest request, Authentication auth) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.update((StaffPrincipal) auth.getPrincipal(), id, request)); }
    @PatchMapping("/{id}/estado") public ResponseEntity<PractitionerDto> status(@PathVariable String id, @Valid @RequestBody PractitionerStatusRequest request, Authentication auth) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status((StaffPrincipal) auth.getPrincipal(), id, request.active())); }
    @PutMapping(value="/{id}/foto", consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ResponseEntity<PractitionerDto> photo(@PathVariable String id, @RequestPart("photo") MultipartFile photo, Authentication auth) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.replacePhoto((StaffPrincipal) auth.getPrincipal(), id, photo)); }
    @DeleteMapping("/{id}/foto") public ResponseEntity<PractitionerDto> removePhoto(@PathVariable String id, Authentication auth) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.removePhoto((StaffPrincipal) auth.getPrincipal(), id)); }
}
