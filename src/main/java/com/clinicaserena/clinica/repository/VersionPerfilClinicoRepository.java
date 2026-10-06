package com.clinicaserena.clinica.repository;
import com.clinicaserena.clinica.entity.VersionPerfilClinico;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface VersionPerfilClinicoRepository extends JpaRepository<VersionPerfilClinico,String> {
    List<VersionPerfilClinico> findByPacienteIdOrderByRegistradaEnDescSecuenciaDesc(String patientId);
}
