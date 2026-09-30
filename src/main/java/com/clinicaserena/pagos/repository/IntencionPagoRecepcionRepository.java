package com.clinicaserena.pagos.repository;

import com.clinicaserena.pagos.entity.IntencionPagoRecepcion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IntencionPagoRecepcionRepository extends JpaRepository<IntencionPagoRecepcion, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from IntencionPagoRecepcion i where i.cuentaRecepcionId = :cuentaId")
    Optional<IntencionPagoRecepcion> findByCuentaIdForUpdate(@Param("cuentaId") String cuentaId);
}
