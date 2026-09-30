package com.clinicaserena.staff.repository;

import com.clinicaserena.staff.entity.CuentaPersonal;
import com.clinicaserena.staff.entity.EstadoCuentaPersonal;
import com.clinicaserena.staff.entity.RolPersonal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CuentaPersonalRepository extends JpaRepository<CuentaPersonal, String> {
    Optional<CuentaPersonal> findByEmailNormalizado(String emailNormalizado);

    List<CuentaPersonal> findAllByOrderByNombreCompletoAsc();

    List<CuentaPersonal> findByRolOrderByNombreCompletoAsc(RolPersonal rol);

    List<CuentaPersonal> findByMedicoIdAndEstado(String medicoId, EstadoCuentaPersonal estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CuentaPersonal c where c.id = :id")
    Optional<CuentaPersonal> findByIdForUpdate(@Param("id") String id);
}
