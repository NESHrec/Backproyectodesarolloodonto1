package com.clinicaserena.catalogo.repository;

import com.clinicaserena.catalogo.entity.Medico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicoRepository extends JpaRepository<Medico, String> {
    List<Medico> findAllByOrderByNombreCompletoAsc();

    List<Medico> findByEspecialidadIdOrderByNombreCompletoAsc(String especialidadId);
}
