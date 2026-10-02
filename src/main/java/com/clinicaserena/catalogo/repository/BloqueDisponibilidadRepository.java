package com.clinicaserena.catalogo.repository;

import com.clinicaserena.catalogo.entity.BloqueDisponibilidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface BloqueDisponibilidadRepository extends JpaRepository<BloqueDisponibilidad, String> {

    List<BloqueDisponibilidad> findByMedicoIdOrderByInicioAsc(String medicoId);

    @Query("""
            select count(bloque) > 0 from BloqueDisponibilidad bloque
            where bloque.medico.id = :medicoId
              and bloque.inicio < :fin
              and bloque.fin > :inicio
            """)
    boolean existsOverlapping(
            @Param("medicoId") String medicoId,
            @Param("inicio") OffsetDateTime inicio,
            @Param("fin") OffsetDateTime fin
    );

    @Query("""
            select bloque from BloqueDisponibilidad bloque
            where bloque.medico.id = :medicoId
              and bloque.disponible = true
              and bloque.inicio >= :inicio
              and bloque.inicio < :finExclusivo
              and bloque.inicio >= :ahora
            order by bloque.inicio
            """)
    List<BloqueDisponibilidad> findDisponibles(
            @Param("medicoId") String medicoId,
            @Param("inicio") OffsetDateTime inicio,
            @Param("finExclusivo") OffsetDateTime finExclusivo,
            @Param("ahora") OffsetDateTime ahora
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select bloque from BloqueDisponibilidad bloque
            where bloque.medico.id = :medicoId and bloque.inicio = :inicio
            """)
    Optional<BloqueDisponibilidad> findByMedicoAndInicioForUpdate(
            @Param("medicoId") String medicoId,
            @Param("inicio") OffsetDateTime inicio
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select bloque from BloqueDisponibilidad bloque where bloque.id = :id")
    Optional<BloqueDisponibilidad> findByIdForUpdate(@Param("id") String id);
}
