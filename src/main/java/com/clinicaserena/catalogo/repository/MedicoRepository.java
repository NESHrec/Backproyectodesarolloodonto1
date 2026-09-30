package com.clinicaserena.catalogo.repository;

import com.clinicaserena.catalogo.entity.Medico;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MedicoRepository extends JpaRepository<Medico, String> {
    List<Medico> findAllByOrderByNombreCompletoAsc();

    List<Medico> findByEspecialidadIdOrderByNombreCompletoAsc(String especialidadId);

    /** Serializa asignaciones concurrentes del mismo profesional a cuentas distintas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Medico m where m.id = :id")
    Optional<Medico> findByIdForUpdate(@Param("id") String id);
}
