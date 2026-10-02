package com.clinicaserena.recepcion.repository;

import com.clinicaserena.recepcion.entity.PacienteAdministrativo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PacienteAdministrativoRepository extends JpaRepository<PacienteAdministrativo, String> {

    Optional<PacienteAdministrativo> findByTelefonoNormalizado(String telefonoNormalizado);

    Optional<PacienteAdministrativo> findByEmailContactoNormalizado(String emailContactoNormalizado);

    @Query(value = """
            with patient_ids as (
                select id as patient_id from pacientes
                union
                select paciente_id from citas
            )
            select ids.patient_id as \"patientId\",
                   coalesce(admin.nombre_completo, account.nombre_completo) as \"fullName\",
                   admin.telefono as \"phone\",
                   coalesce(admin.email_contacto, account.email_normalizado) as \"email\",
                   case
                       when account.paciente_id is not null and admin.paciente_id is not null then 'AUTORREGISTRADO+EXPEDIENTE_ADMINISTRATIVO'
                       when account.paciente_id is not null then 'AUTORREGISTRADO'
                       when admin.paciente_id is not null then 'EXPEDIENTE_ADMINISTRATIVO'
                       else 'HISTORICO'
                   end as \"recordType\",
                   (account.paciente_id is not null) as \"patientAccountLinked\",
                   coalesce(admin.creado_en, patient.creado_en) as \"createdAt\"
            from patient_ids ids
            left join pacientes patient on patient.id = ids.patient_id
            left join pacientes_administrativos admin on admin.paciente_id = ids.patient_id
            left join cuentas_paciente account on account.paciente_id = ids.patient_id
            where :search = ''
               or ids.patient_id ilike concat('%', :search, '%')
               or lower(coalesce(admin.nombre_completo, account.nombre_completo, '')) like lower(concat('%', :search, '%'))
               or admin.telefono_normalizado like concat('%', :search, '%')
               or lower(coalesce(admin.email_contacto_normalizado, account.email_normalizado, '')) like lower(concat('%', :search, '%'))
            order by coalesce(admin.creado_en, patient.creado_en) desc nulls last, ids.patient_id
            """,
            countQuery = """
            with patient_ids as (
                select id as patient_id from pacientes
                union
                select paciente_id from citas
            )
            select count(*)
            from patient_ids ids
            left join pacientes patient on patient.id = ids.patient_id
            left join pacientes_administrativos admin on admin.paciente_id = ids.patient_id
            left join cuentas_paciente account on account.paciente_id = ids.patient_id
            where :search = ''
               or ids.patient_id ilike concat('%', :search, '%')
               or lower(coalesce(admin.nombre_completo, account.nombre_completo, '')) like lower(concat('%', :search, '%'))
               or admin.telefono_normalizado like concat('%', :search, '%')
               or lower(coalesce(admin.email_contacto_normalizado, account.email_normalizado, '')) like lower(concat('%', :search, '%'))
            """,
            nativeQuery = true)
    Page<ReceptionPatientProjection> search(@Param("search") String search, Pageable pageable);
}
