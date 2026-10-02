package com.clinicaserena.catalogo.service;

import com.clinicaserena.catalogo.dto.SpecialtyDto;
import com.clinicaserena.catalogo.dto.UpsertSpecialtyRequest;
import com.clinicaserena.catalogo.entity.Especialidad;
import com.clinicaserena.catalogo.mapper.CatalogoMapper;
import com.clinicaserena.catalogo.repository.EspecialidadRepository;
import com.clinicaserena.common.exception.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class SpecialtyAdminService {

    private final EspecialidadRepository repository;
    private final CatalogoMapper mapper;

    public SpecialtyAdminService(EspecialidadRepository repository, CatalogoMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<SpecialtyDto> list() {
        return repository.findAllByOrderByNombreAsc().stream().map(mapper::toDto).toList();
    }

    @Transactional
    public SpecialtyDto create(UpsertSpecialtyRequest request) {
        String name = normalizedName(request.name());
        String description = normalizedDescription(request.description());
        ensureValid(name, description);
        ensureUnique(name, null);
        try {
            return mapper.toDto(repository.saveAndFlush(Especialidad.crear(
                    UUID.randomUUID().toString(), name, description)));
        } catch (DataIntegrityViolationException collision) {
            throw duplicate();
        }
    }

    @Transactional
    public SpecialtyDto update(String id, UpsertSpecialtyRequest request) {
        String name = normalizedName(request.name());
        String description = normalizedDescription(request.description());
        ensureValid(name, description);
        Especialidad specialty = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPECIALTY_NOT_FOUND",
                        "La especialidad no existe"));
        ensureUnique(name, id);
        try {
            specialty.actualizar(name, description);
            return mapper.toDto(repository.saveAndFlush(specialty));
        } catch (DataIntegrityViolationException collision) {
            throw duplicate();
        }
    }

    private void ensureUnique(String normalizedName, String excludedId) {
        boolean duplicate = repository.findAll().stream()
                .filter(item -> excludedId == null || !item.getId().equals(excludedId))
                .anyMatch(item -> normalizedName(item.getNombre()).equals(normalizedName));
        if (duplicate) throw duplicate();
    }

    private static String normalizedName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String normalizedDescription(String value) {
        return value == null ? "" : value.trim();
    }

    private static void ensureValid(String name, String description) {
        if (name.isBlank() || name.length() > 120) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SPECIALTY_NAME_INVALID",
                    "El nombre debe tener entre 1 y 120 caracteres");
        }
        if (description.isBlank() || description.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SPECIALTY_DESCRIPTION_INVALID",
                    "La descripción debe tener entre 1 y 500 caracteres");
        }
    }

    private static ApiException duplicate() {
        return new ApiException(HttpStatus.CONFLICT, "SPECIALTY_DUPLICATE",
                "Ya existe una especialidad con ese nombre");
    }
}
