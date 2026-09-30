package com.clinicaserena.clinica.repository;

import com.clinicaserena.clinica.entity.AtencionClinica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AtencionClinicaRepository extends JpaRepository<AtencionClinica, String> {

    boolean existsByCitaId(String citaId);

    Optional<AtencionClinica> findByCitaId(String citaId);

    List<AtencionClinica> findByPacienteIdOrderByRegistradaEnDesc(String pacienteId);

    @Query("select a.citaId from AtencionClinica a where a.citaId in :citaIds")
    List<String> findDocumentedCitaIds(@Param("citaIds") Collection<String> citaIds);
}
