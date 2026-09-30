package com.clinicaserena.pagos.repository;

import com.clinicaserena.pagos.entity.PagoCita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PagoCitaRepository extends JpaRepository<PagoCita, String> {

    List<PagoCita> findByCitaIdOrderByRegistradoEnAsc(String citaId);

    Optional<PagoCita> findByCitaIdAndIdempotencyKey(String citaId, String idempotencyKey);

    @Query("select coalesce(sum(p.montoCentavos), 0) from PagoCita p where p.citaId = :citaId")
    long sumPaidByCitaId(@Param("citaId") String citaId);
}
