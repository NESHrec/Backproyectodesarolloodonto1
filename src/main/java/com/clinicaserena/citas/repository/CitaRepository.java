package com.clinicaserena.citas.repository;

import com.clinicaserena.citas.entity.Cita;
import com.clinicaserena.citas.entity.EstadoCita;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CitaRepository extends JpaRepository<Cita, String> {
    boolean existsByBloqueIdAndEstadoNot(String bloqueId, EstadoCita estado);
}
