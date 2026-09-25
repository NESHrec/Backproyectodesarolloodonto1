package com.clinicaserena.catalogo.repository;

import com.clinicaserena.catalogo.entity.Especialidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EspecialidadRepository extends JpaRepository<Especialidad, String> {
    List<Especialidad> findAllByOrderByNombreAsc();
}
