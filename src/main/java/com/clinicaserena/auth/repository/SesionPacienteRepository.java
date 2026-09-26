package com.clinicaserena.auth.repository;

import com.clinicaserena.auth.entity.SesionPaciente;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SesionPacienteRepository extends JpaRepository<SesionPaciente, String> {

    @EntityGraph(attributePaths = {"cuenta", "cuenta.paciente"})
    Optional<SesionPaciente> findByTokenHash(String tokenHash);
}
