package com.clinicaserena.odontograma.repository;

import com.clinicaserena.odontograma.entity.ObservacionOdontograma;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ObservacionOdontogramaRepository extends JpaRepository<ObservacionOdontograma, String> {
    List<ObservacionOdontograma> findByPacienteIdOrderByRegistradaEnDesc(String pacienteId);
}
