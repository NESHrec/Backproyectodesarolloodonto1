package com.clinicaserena.citas.repository;

import com.clinicaserena.citas.entity.Cita;
import com.clinicaserena.citas.entity.EstadoCita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;

import java.util.List;

public interface CitaRepository extends JpaRepository<Cita, String> {
    boolean existsByBloqueIdAndEstadoNot(String bloqueId, EstadoCita estado);

    List<Cita> findByPacienteIdOrderByProgramadaEnDesc(String pacienteId);

    List<Cita> findByPacienteIdAndEstadoOrderByProgramadaEnDesc(String pacienteId, EstadoCita estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cita c where c.id = :id")
    Optional<Cita> findByIdForUpdate(@Param("id") String id);

    @Query("select c from Cita c where c.programadaEn >= :from and c.programadaEn < :to "
            + "and (:status is null or c.estado = :status) order by c.programadaEn asc")
    List<Cita> findForStaffAgenda(@Param("from") OffsetDateTime from,
                                  @Param("to") OffsetDateTime to,
                                  @Param("status") EstadoCita status,
                                  Pageable pageable);

    @Query("select c from Cita c where c.medico.id = :medicoId and c.programadaEn >= :from "
            + "and c.programadaEn < :to and (:status is null or c.estado = :status) order by c.programadaEn asc")
    List<Cita> findForPractitioner(@Param("medicoId") String medicoId,
                                   @Param("from") OffsetDateTime from,
                                   @Param("to") OffsetDateTime to,
                                   @Param("status") EstadoCita status,
                                   Pageable pageable);
}
