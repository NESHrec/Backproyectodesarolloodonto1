package com.clinicaserena.staff.repository;

import com.clinicaserena.staff.entity.SesionPersonal;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface SesionPersonalRepository extends JpaRepository<SesionPersonal, String> {

    @EntityGraph(attributePaths = "cuenta")
    Optional<SesionPersonal> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update SesionPersonal s set s.revocadaEn = :ahora where s.cuenta.id = :cuentaId and s.revocadaEn is null")
    int revokeAllByCuentaId(@Param("cuentaId") String cuentaId, @Param("ahora") OffsetDateTime ahora);
}
