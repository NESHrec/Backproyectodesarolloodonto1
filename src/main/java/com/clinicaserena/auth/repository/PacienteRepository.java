package com.clinicaserena.auth.repository;
import com.clinicaserena.auth.entity.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PacienteRepository extends JpaRepository<Paciente, String> {}
