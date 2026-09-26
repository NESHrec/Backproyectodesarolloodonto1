package com.clinicaserena.auth.repository;

import com.clinicaserena.auth.entity.CuentaPaciente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CuentaPacienteRepository extends JpaRepository<CuentaPaciente, String> {

    Optional<CuentaPaciente> findByEmailNormalizado(String emailNormalizado);
}
