package com.clinicaserena.clinica.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.time.OffsetDateTime;

@Entity @Immutable @Table(name="versiones_perfil_clinico")
public class VersionPerfilClinico {
    @Id @Column(length=36,updatable=false) private String id;
    @Column(nullable=false,insertable=false,updatable=false) private Long secuencia;
    @Column(name="expediente_id",length=36,nullable=false,updatable=false) private String expedienteId;
    @Column(name="paciente_id",length=36,nullable=false,updatable=false) private String pacienteId;
    @Column(length=2000,updatable=false) private String alergias;
    @Column(name="condiciones_relevantes",length=2000,updatable=false) private String condicionesRelevantes;
    @Column(name="medicamentos_actuales",length=2000,updatable=false) private String medicamentosActuales;
    @Column(name="antecedentes_odontologicos",length=2000,updatable=false) private String antecedentesOdontologicos;
    @Column(name="autor_personal_id",length=36,nullable=false,updatable=false) private String autorPersonalId;
    @Column(name="registrada_en",nullable=false,updatable=false) private OffsetDateTime registradaEn;
    protected VersionPerfilClinico() {}
    public static VersionPerfilClinico registrar(String id,String expediente,String paciente,String alergias,String condiciones,String medicamentos,String antecedentes,String autor,OffsetDateTime fecha){
        VersionPerfilClinico v=new VersionPerfilClinico();v.id=id;v.expedienteId=expediente;v.pacienteId=paciente;v.alergias=alergias;
        v.condicionesRelevantes=condiciones;v.medicamentosActuales=medicamentos;v.antecedentesOdontologicos=antecedentes;v.autorPersonalId=autor;v.registradaEn=fecha;return v;
    }
    public String getId(){return id;} public String getPacienteId(){return pacienteId;} public String getAlergias(){return alergias;}
    public String getCondicionesRelevantes(){return condicionesRelevantes;} public String getMedicamentosActuales(){return medicamentosActuales;}
    public String getAntecedentesOdontologicos(){return antecedentesOdontologicos;} public String getAutorPersonalId(){return autorPersonalId;}
    public OffsetDateTime getRegistradaEn(){return registradaEn;}
    public Long getSecuencia(){return secuencia;}
}
