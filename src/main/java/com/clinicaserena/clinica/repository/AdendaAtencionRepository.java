package com.clinicaserena.clinica.repository;
import com.clinicaserena.clinica.entity.AdendaAtencion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection; import java.util.List;
public interface AdendaAtencionRepository extends JpaRepository<AdendaAtencion,String> {
    List<AdendaAtencion> findByAtencionIdInOrderByRegistradaEnAscIdAsc(Collection<String> ids);
}
