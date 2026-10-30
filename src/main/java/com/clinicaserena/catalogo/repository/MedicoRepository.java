package com.clinicaserena.catalogo.repository;

import com.clinicaserena.catalogo.entity.Medico;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MedicoRepository extends JpaRepository<Medico, String> {
    List<Medico> findAllByOrderByNombreCompletoAsc();

    List<Medico> findByEspecialidadIdOrderByNombreCompletoAsc(String especialidadId);

    List<Medico> findByActivoTrueOrderByNombreCompletoAsc();
    List<Medico> findByActivoTrueAndEspecialidadIdOrderByNombreCompletoAsc(String especialidadId);
    boolean existsByFotoClave(String fotoClave);
    boolean existsByNumeroColegiadoIgnoreCaseAndIdNot(String numeroColegiado, String id);
    boolean existsByNumeroColegiadoIgnoreCase(String numeroColegiado);

    @Query(value = "select exists(select 1 from medicos where upper(regexp_replace(btrim(numero_colegiado), '[[:space:]]+', ' ', 'g')) = :normalizado and (:id is null or id <> :id))", nativeQuery = true)
    boolean existsByNormalizedLicense(@Param("normalizado") String normalizado, @Param("id") String id);

    /** Serializa asignaciones concurrentes del mismo profesional a cuentas distintas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Medico m where m.id = :id")
    Optional<Medico> findByIdForUpdate(@Param("id") String id);

    /**
     * Hard-delete is allowed only when the practitioner has no operational,
     * clinical, account, or linking-history references.
     */
    @Query(value = """
            select case
                when exists (select 1 from cuentas_personal where medico_id = :id) then 'STAFF_ACCOUNT'
                when exists (select 1 from citas where medico_id = :id) then 'APPOINTMENTS'
                when exists (select 1 from atenciones_clinicas where medico_id = :id) then 'CLINICAL_RECORDS'
                when exists (select 1 from observaciones_odontograma where medico_id = :id) then 'DENTAL_RECORDS'
                when exists (select 1 from historial_vinculacion_medico where medico_anterior_id = :id or medico_nuevo_id = :id) then 'LINK_HISTORY'
                else null
            end
            """, nativeQuery = true)
    String findDeletionBlocker(@Param("id") String id);
}
