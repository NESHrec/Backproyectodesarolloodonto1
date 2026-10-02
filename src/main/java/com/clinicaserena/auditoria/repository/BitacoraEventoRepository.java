package com.clinicaserena.auditoria.repository;

import com.clinicaserena.auditoria.entity.BitacoraEvento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BitacoraEventoRepository extends JpaRepository<BitacoraEvento, String> {
    Page<BitacoraEvento> findAllByOrderByOcurridoEnDesc(Pageable pageable);
}
