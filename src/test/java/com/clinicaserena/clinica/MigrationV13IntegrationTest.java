package com.clinicaserena.clinica;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.UUID;
import static com.clinicaserena.clinica.ClinicalFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

class MigrationV13IntegrationTest {
    @Test
    void migraBaseVaciaYBaseV12ConObservacionHistoricaSinAlterarla() {
        String host=System.getenv().getOrDefault("DB_HOST","localhost");
        String port=System.getenv().getOrDefault("DB_PORT","5432");
        String database=System.getenv().getOrDefault("DB_NAME","clinica_serena");
        String user=System.getenv().getOrDefault("DB_USER","clinica_app");
        String password=System.getenv().getOrDefault("DB_PASSWORD","");
        String schema="migration_v13_"+UUID.randomUUID().toString().replace("-","");
        String url="jdbc:postgresql://"+host+":"+port+"/"+database+"?currentSchema="+schema;
        DriverManagerDataSource dataSource=new DriverManagerDataSource(url,user,password);
        Flyway v12=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).createSchemas(true).target("12").load();
        try {
            assertThat(v12.migrate().success).isTrue();
            JdbcTemplate jdbc=new JdbcTemplate(dataSource);
            ClinicalFixture fixture=new ClinicalFixture(jdbc);
            fixture.create(new BCryptPasswordEncoder(),true);
            String legacyId="99900000-0000-0000-0000-000000000013";
            jdbc.update("INSERT INTO observaciones_odontograma(id,paciente_id,cita_id,medico_id,autor_personal_id,pieza_dental,observacion,registrada_en) VALUES (?,?,?,?,?,?,?,?)",
                    legacyId,PATIENT_X,APPT_X_ONE_PENDING,PRACTITIONER_ONE,DOCTOR_ONE,16,"Observación histórica sintética",fixture.base());
            Flyway latest=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
            assertThat(latest.migrate().success).isTrue();
            assertThat(jdbc.queryForObject("SELECT observacion FROM observaciones_odontograma WHERE id=?",String.class,legacyId)).isEqualTo("Observación histórica sintética");
            assertThat(jdbc.queryForObject("SELECT superficie FROM observaciones_odontograma WHERE id=?",String.class,legacyId)).isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='13'",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM versiones_perfil_clinico",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM adendas_atencion",Integer.class)).isZero();
        } finally {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).cleanDisabled(false).load().clean();
        }
    }
}
