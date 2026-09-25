package com.clinicaserena.catalogo.service;

import com.clinicaserena.catalogo.dto.AvailabilitySlotDto;
import com.clinicaserena.catalogo.dto.PractitionerDto;
import com.clinicaserena.catalogo.dto.SpecialtyDto;
import com.clinicaserena.catalogo.mapper.CatalogoMapper;
import com.clinicaserena.catalogo.repository.BloqueDisponibilidadRepository;
import com.clinicaserena.catalogo.repository.EspecialidadRepository;
import com.clinicaserena.catalogo.repository.MedicoRepository;
import com.clinicaserena.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CatalogoService {

    private final EspecialidadRepository especialidadRepository;
    private final MedicoRepository medicoRepository;
    private final BloqueDisponibilidadRepository disponibilidadRepository;
    private final CatalogoMapper mapper;

    public CatalogoService(
            EspecialidadRepository especialidadRepository,
            MedicoRepository medicoRepository,
            BloqueDisponibilidadRepository disponibilidadRepository,
            CatalogoMapper mapper
    ) {
        this.especialidadRepository = especialidadRepository;
        this.medicoRepository = medicoRepository;
        this.disponibilidadRepository = disponibilidadRepository;
        this.mapper = mapper;
    }

    public List<SpecialtyDto> listarEspecialidades() {
        return especialidadRepository.findAllByOrderByNombreAsc().stream().map(mapper::toDto).toList();
    }

    public List<PractitionerDto> listarMedicos(String especialidadId) {
        var medicos = especialidadId == null || especialidadId.isBlank()
                ? medicoRepository.findAllByOrderByNombreCompletoAsc()
                : medicoRepository.findByEspecialidadIdOrderByNombreCompletoAsc(especialidadId);
        return medicos.stream().map(mapper::toDto).toList();
    }

    public List<AvailabilitySlotDto> obtenerDisponibilidad(
            String medicoId,
            LocalDate desde,
            LocalDate hasta
    ) {
        if (!medicoRepository.existsById(medicoId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PRACTITIONER_NOT_FOUND", "El médico no existe");
        }

        LocalDate fechaDesde = desde == null ? LocalDate.now(ZoneOffset.UTC) : desde;
        LocalDate fechaHasta = hasta == null ? fechaDesde.plusDays(30) : hasta;
        if (fechaHasta.isBefore(fechaDesde)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "La fecha hasta no puede ser anterior a desde");
        }

        OffsetDateTime inicio = fechaDesde.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime finExclusivo = fechaHasta.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return disponibilidadRepository.findDisponibles(medicoId, inicio, finExclusivo)
                .stream()
                .map(mapper::toDto)
                .toList();
    }
}
