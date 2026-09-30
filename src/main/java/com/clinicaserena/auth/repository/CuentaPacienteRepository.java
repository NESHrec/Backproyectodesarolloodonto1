package com.clinicaserena.auth.repository;

import com.clinicaserena.auth.entity.CuentaPaciente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CuentaPacienteRepository extends JpaRepository<CuentaPaciente, String> {

    Optional<CuentaPaciente> findByEmailNormalizado(String emailNormalizado);

    List<CuentaPaciente> findByPacienteIdIn(Collection<String> pacienteIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CuentaPaciente c where c.id = :id")
    Optional<CuentaPaciente> findByIdForUpdate(@Param("id") String id);
}
