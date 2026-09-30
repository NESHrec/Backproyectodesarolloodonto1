package com.clinicaserena.clinica.repository;

import com.clinicaserena.clinica.entity.ExpedienteClinico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface ExpedienteClinicoRepository extends JpaRepository<ExpedienteClinico, String> {

    Optional<ExpedienteClinico> findByPacienteId(String pacienteId);

    /** Crea el expediente si no existe; atenciones concurrentes del mismo paciente comparten la fila. */
    @Modifying
    @Query(value = "INSERT INTO expedientes_clinicos (id, paciente_id, creado_en, creado_por_personal_id) "
            + "VALUES (:id, :pacienteId, :ahora, :autorId) ON CONFLICT (paciente_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("id") String id, @Param("pacienteId") String pacienteId,
                       @Param("ahora") OffsetDateTime ahora, @Param("autorId") String autorId);
}
