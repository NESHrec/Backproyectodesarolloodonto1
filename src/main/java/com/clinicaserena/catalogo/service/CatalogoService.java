package com.clinicaserena.catalogo.service;

import com.clinicaserena.catalogo.dto.AvailabilitySlotDto;
import com.clinicaserena.catalogo.dto.PractitionerDto;
import com.clinicaserena.catalogo.dto.SpecialtyDto;
import com.clinicaserena.catalogo.mapper.CatalogoMapper;
import com.clinicaserena.catalogo.repository.BloqueDisponibilidadRepository;
import com.clinicaserena.catalogo.repository.EspecialidadRepository;
import com.clinicaserena.catalogo.repository.MedicoRepository;
import com.clinicaserena.common.exception.ApiException;
import com.clinicaserena.common.time.BusinessTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CatalogoService {

    private final EspecialidadRepository especialidadRepository;
    private final MedicoRepository medicoRepository;
    private final BloqueDisponibilidadRepository disponibilidadRepository;
    private final CatalogoMapper mapper;
    private final Clock clock;

    public CatalogoService(
            EspecialidadRepository especialidadRepository,
            MedicoRepository medicoRepository,
            BloqueDisponibilidadRepository disponibilidadRepository,
            CatalogoMapper mapper,
            Clock clock
    ) {
        this.especialidadRepository = especialidadRepository;
        this.medicoRepository = medicoRepository;
        this.disponibilidadRepository = disponibilidadRepository;
        this.mapper = mapper;
        this.clock = clock;
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

        OffsetDateTime ahora = OffsetDateTime.now(clock);
        LocalDate fechaDesde = desde == null ? ahora.atZoneSameInstant(BusinessTime.ZONE).toLocalDate() : desde;
        LocalDate fechaHasta = hasta == null ? fechaDesde.plusDays(30) : hasta;
        if (fechaHasta.isBefore(fechaDesde)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "La fecha hasta no puede ser anterior a desde");
        }

        OffsetDateTime inicio = fechaDesde.atStartOfDay(BusinessTime.ZONE).toOffsetDateTime();
        OffsetDateTime finExclusivo = fechaHasta.plusDays(1).atStartOfDay(BusinessTime.ZONE).toOffsetDateTime();
        return disponibilidadRepository.findDisponibles(medicoId, inicio, finExclusivo, ahora)
                .stream()
                .map(mapper::toDto)
                .toList();
    }
}
