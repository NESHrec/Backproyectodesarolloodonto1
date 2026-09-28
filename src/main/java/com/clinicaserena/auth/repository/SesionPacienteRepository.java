package com.clinicaserena.auth.repository;

import com.clinicaserena.auth.entity.SesionPaciente;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SesionPacienteRepository extends JpaRepository<SesionPaciente, String> {

    @EntityGraph(attributePaths = {"cuenta", "cuenta.paciente"})
    Optional<SesionPaciente> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update SesionPaciente s set s.revocadaEn = :ahora where s.cuenta.id = :cuentaId and s.revocadaEn is null")
    int revokeAllByCuentaId(@Param("cuentaId") String cuentaId, @Param("ahora") OffsetDateTime ahora);
}
