package com.clinicaserena.catalogo.repository;

import com.clinicaserena.catalogo.entity.Especialidad;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;

public interface EspecialidadRepository extends JpaRepository<Especialidad, String> {
    List<Especialidad> findAllByOrderByNombreAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Especialidad e where e.id = :id")
    java.util.Optional<Especialidad> findByIdForUpdate(@Param("id") String id);

    /** Hard-delete is safe only when no practitioner or appointment references the specialty. */
    @Query(value = """
            select case
                when exists (select 1 from medicos where especialidad_id = :id) then 'PRACTITIONERS'
                when exists (select 1 from citas where especialidad_id = :id) then 'APPOINTMENTS'
                else null
            end
            """, nativeQuery = true)
    String findDeletionBlocker(@Param("id") String id);
}
