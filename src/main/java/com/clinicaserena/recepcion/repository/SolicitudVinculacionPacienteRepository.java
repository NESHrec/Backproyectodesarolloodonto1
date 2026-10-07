package com.clinicaserena.recepcion.repository;
import com.clinicaserena.recepcion.entity.SolicitudVinculacionPaciente;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface SolicitudVinculacionPacienteRepository extends JpaRepository<SolicitudVinculacionPaciente,String> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from SolicitudVinculacionPaciente s where s.id=:id")
 Optional<SolicitudVinculacionPaciente> findByIdForUpdate(@Param("id") String id);
 Optional<SolicitudVinculacionPaciente> findByPacienteAdministrativoIdAndEstado(String patientId,String estado);
}
