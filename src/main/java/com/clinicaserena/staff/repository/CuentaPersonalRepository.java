package com.clinicaserena.staff.repository;

import com.clinicaserena.staff.entity.CuentaPersonal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CuentaPersonalRepository extends JpaRepository<CuentaPersonal, String> {
    Optional<CuentaPersonal> findByEmailNormalizado(String emailNormalizado);
}
