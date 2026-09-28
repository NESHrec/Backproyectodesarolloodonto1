package com.clinicaserena.auth.repository;
import com.clinicaserena.auth.entity.*;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
public interface TokenCuentaPacienteRepository extends JpaRepository<TokenCuentaPaciente, String> {
    @EntityGraph(attributePaths = {"cuenta", "cuenta.paciente"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TokenCuentaPaciente> findByTokenHashAndTipo(String hash, TipoTokenCuenta tipo);

    @Modifying
    @Query("update TokenCuentaPaciente t set t.usadoEn = :ahora where t.cuenta.id = :cuentaId and t.tipo = :tipo and t.usadoEn is null")
    int invalidateActive(@Param("cuentaId") String cuentaId, @Param("tipo") TipoTokenCuenta tipo,
                         @Param("ahora") OffsetDateTime ahora);
}
