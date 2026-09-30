package com.clinicaserena.clinica.repository;

import com.clinicaserena.clinica.entity.RecetaItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RecetaItemRepository extends JpaRepository<RecetaItem, String> {

    List<RecetaItem> findByAtencionIdInOrderByAtencionIdAscOrdenAsc(Collection<String> atencionIds);
}
