package com.clinicaserena.catalogo.controller;

import com.clinicaserena.catalogo.dto.AvailabilitySlotDto;
import com.clinicaserena.catalogo.dto.PractitionerDto;
import com.clinicaserena.catalogo.dto.SpecialtyDto;
import com.clinicaserena.catalogo.service.CatalogoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CatalogoController {

    private final CatalogoService catalogoService;

    public CatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/especialidades")
    public List<SpecialtyDto> listarEspecialidades() {
        return catalogoService.listarEspecialidades();
    }

    @GetMapping("/medicos")
    public List<PractitionerDto> listarMedicos(
            @RequestParam(required = false) String specialtyId
    ) {
        return catalogoService.listarMedicos(specialtyId);
    }

    @GetMapping("/medicos/{medicoId}/disponibilidad")
    public List<AvailabilitySlotDto> obtenerDisponibilidad(
            @PathVariable String medicoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta
    ) {
        return catalogoService.obtenerDisponibilidad(medicoId, desde, hasta);
    }
}
