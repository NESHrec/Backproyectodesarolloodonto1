package com.clinicaserena.catalogo.repository;

import com.clinicaserena.catalogo.entity.BloqueDisponibilidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface BloqueDisponibilidadRepository extends JpaRepository<BloqueDisponibilidad, String> {

    @Query("""
            select bloque from BloqueDisponibilidad bloque
            where bloque.medico.id = :medicoId
              and bloque.disponible = true
              and bloque.inicio >= :inicio
              and bloque.inicio < :finExclusivo
            order by bloque.inicio
            """)
    List<BloqueDisponibilidad> findDisponibles(
            @Param("medicoId") String medicoId,
            @Param("inicio") OffsetDateTime inicio,
            @Param("finExclusivo") OffsetDateTime finExclusivo
    );
}
