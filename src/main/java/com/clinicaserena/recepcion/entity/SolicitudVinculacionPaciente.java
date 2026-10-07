package com.clinicaserena.recepcion.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "solicitudes_vinculacion_paciente")
public class SolicitudVinculacionPaciente {
    @Id @Column(length = 36, updatable = false) private String id;
    @Column(name="paciente_administrativo_id", nullable=false, length=36) private String pacienteAdministrativoId;
    @Column(name="codigo_hash", nullable=false, length=64) private String codigoHash;
    @Column(name="creado_por_personal_id", nullable=false, length=36, updatable=false) private String creadoPorPersonalId;
    @Column(name="creado_en", nullable=false, updatable=false) private OffsetDateTime creadoEn;
    @Column(name="expira_en", nullable=false) private OffsetDateTime expiraEn;
    @Column(nullable=false) private short intentos;
    @Column(name="max_intentos", nullable=false) private short maxIntentos;
    @Column(nullable=false, length=20) private String estado;
    @Column(name="confirmado_por_cuenta_id", length=36) private String confirmadoPorCuentaId;
    @Column(name="confirmado_en") private OffsetDateTime confirmadoEn;
    @Column(name="revocado_en") private OffsetDateTime revocadoEn;
    protected SolicitudVinculacionPaciente() {}
    public static SolicitudVinculacionPaciente crear(String id,String patientId,String hash,String staffId,OffsetDateTime now,OffsetDateTime expiry) {
        var r=new SolicitudVinculacionPaciente(); r.id=id;r.pacienteAdministrativoId=patientId;r.codigoHash=hash;
        r.creadoPorPersonalId=staffId;r.creadoEn=now;r.expiraEn=expiry;r.maxIntentos=5;r.estado="PENDIENTE";return r;
    }
    public String getId(){return id;} public String getPacienteAdministrativoId(){return pacienteAdministrativoId;}
    public String getCodigoHash(){return codigoHash;} public OffsetDateTime getExpiraEn(){return expiraEn;}
    public short getIntentos(){return intentos;} public short getMaxIntentos(){return maxIntentos;} public String getEstado(){return estado;}
    public void expirar(){estado="EXPIRADA";} public void revocar(OffsetDateTime now){estado="REVOCADA";revocadoEn=now;codigoHash="0".repeat(64);}
    public void fallo(){intentos++;if(intentos>=maxIntentos){estado="BLOQUEADA";codigoHash="0".repeat(64);}}
    public void confirmar(String accountId,OffsetDateTime now){estado="CONFIRMADA";confirmadoPorCuentaId=accountId;confirmadoEn=now;codigoHash="0".repeat(64);}
}
