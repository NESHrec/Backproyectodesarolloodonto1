package com.clinicaserena.catalogo.mapper;

import com.clinicaserena.catalogo.dto.AvailabilitySlotDto;
import com.clinicaserena.catalogo.dto.PractitionerDto;
import com.clinicaserena.catalogo.dto.SpecialtyDto;
import com.clinicaserena.catalogo.entity.BloqueDisponibilidad;
import com.clinicaserena.catalogo.entity.Especialidad;
import com.clinicaserena.catalogo.entity.Medico;
import org.springframework.stereotype.Component;

@Component
public class CatalogoMapper {

    public SpecialtyDto toDto(Especialidad especialidad) {
        return new SpecialtyDto(especialidad.getId(), especialidad.getNombre(), especialidad.getDescripcion());
    }

    public PractitionerDto toDto(Medico medico) {
        return new PractitionerDto(
                medico.getId(),
                medico.getNombreCompleto(),
                medico.getEspecialidad().getId(),
                medico.getEspecialidad().getNombre(),
                medico.getNumeroColegiado(),
                medico.isActivo(),
                medico.getFotoClave() == null ? null : "/api/v1/medicos/" + medico.getId() + "/foto?v=" + medico.getFotoClave()
        );
    }

    public AvailabilitySlotDto toDto(BloqueDisponibilidad bloque) {
        return new AvailabilitySlotDto(
                bloque.getId(),
                bloque.getMedico().getId(),
                bloque.getInicio(),
                bloque.getFin()
        );
    }
}
