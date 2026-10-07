package com.clinicaserena.pagos.service;

import com.clinicaserena.citas.entity.Cita;
import com.clinicaserena.citas.entity.EstadoCita;
import com.clinicaserena.citas.repository.CitaRepository;
import com.clinicaserena.clinica.repository.AtencionClinicaRepository;
import com.clinicaserena.common.exception.ApiException;
import com.clinicaserena.pagos.dto.BillingAppointmentResponse;
import com.clinicaserena.pagos.dto.PaymentResponse;
import com.clinicaserena.pagos.dto.PaymentIntentResponse;
import com.clinicaserena.pagos.dto.RegisterPaymentRequest;
import com.clinicaserena.pagos.dto.SetAppointmentChargeRequest;
import com.clinicaserena.pagos.entity.CargoCitaAuditoria;
import com.clinicaserena.pagos.entity.PagoCita;
import com.clinicaserena.pagos.entity.EstadoIntencionPago;
import com.clinicaserena.pagos.entity.IntencionPagoRecepcion;
import com.clinicaserena.pagos.repository.CargoCitaAuditoriaRepository;
import com.clinicaserena.pagos.repository.PagoCitaRepository;
import com.clinicaserena.pagos.repository.IntencionPagoRecepcionRepository;
import com.clinicaserena.staff.entity.RolPersonal;
import com.clinicaserena.staff.security.StaffPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.clinicaserena.auditoria.service.BitacoraService;

@Service
public class ReceptionBillingService {

    private static final String CURRENCY = "GTQ";

    private final CitaRepository citaRepository;
    private final AtencionClinicaRepository atencionRepository;
    private final PagoCitaRepository pagoRepository;
    private final CargoCitaAuditoriaRepository cargoRepository;
    private final IntencionPagoRecepcionRepository intentRepository;
    private final Clock clock;
    private final BitacoraService audit;

    public ReceptionBillingService(CitaRepository citaRepository, AtencionClinicaRepository atencionRepository,
                                   PagoCitaRepository pagoRepository,
                                   CargoCitaAuditoriaRepository cargoRepository,
                                   IntencionPagoRecepcionRepository intentRepository,
                                   Clock clock, BitacoraService audit) {
        this.citaRepository = citaRepository;
        this.atencionRepository = atencionRepository;
        this.pagoRepository = pagoRepository;
        this.cargoRepository = cargoRepository;
        this.intentRepository = intentRepository;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<BillingAppointmentResponse> list(StaffPrincipal principal, int limit) {
        requireReception(principal);
        if (limit < 1 || limit > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LIMIT_INVALID", "El límite debe estar entre 1 y 100");
        }
        return citaRepository.findByEstadoOrderByProgramadaEnDesc(EstadoCita.COMPLETADA, PageRequest.of(0, limit))
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BillingAppointmentResponse get(StaffPrincipal principal, String appointmentId) {
        requireReception(principal);
        Cita cita = findAppointment(appointmentId);
        return toResponse(cita);
    }

    @Transactional
    public BillingAppointmentResponse setCharge(StaffPrincipal principal, String appointmentId,
                                                SetAppointmentChargeRequest request) {
        requireReception(principal);
        String currency = request.currency() == null || request.currency().isBlank() ? CURRENCY : request.currency();
        if (!CURRENCY.equals(currency)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENCY_UNSUPPORTED", "La moneda admitida es GTQ");
        }
        Cita cita = lockBillableAppointment(appointmentId);
        if (cita.getMontoCentavos() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "CHARGE_ALREADY_DEFINED", "La cita ya tiene cargo definido");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        cargoRepository.saveAndFlush(CargoCitaAuditoria.registrar(UUID.randomUUID().toString(), cita.getId(),
                cita.getMontoCentavos(), request.amount(), cita.getMoneda(), currency, principal.accountId(), now));
        cita.fijarCargo(request.amount(), currency, now);
        BillingAppointmentResponse response=toResponse(citaRepository.saveAndFlush(cita));
        audit.record(principal,"APPOINTMENT_CHARGE_SET","CITA",appointmentId,now);
        return response;
    }

    @Transactional
    public BillingAppointmentResponse registerPayment(StaffPrincipal principal, String appointmentId,
                                                      RegisterPaymentRequest request) {
        requireReception(principal);
        IntencionPagoRecepcion activeIntent = intentRepository.findByCuentaIdForUpdate(principal.accountId()).orElse(null);
        if (activeIntent != null && !sameIntent(activeIntent, appointmentId, request)) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_INTENT_ACTIVE",
                    "Existe una intencion de pago pendiente; debe resolverse antes de crear otra");
        }
        Cita cita = lockBillableAppointment(appointmentId);
        PagoCita existing = pagoRepository.findByCitaIdAndIdempotencyKey(cita.getId(), request.idempotencyKey())
                .orElse(null);
        if (existing != null) {
            if (!sameIntent(existing, request)) {
                throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_IDEMPOTENCY_KEY_REUSED",
                        "La clave de idempotencia pertenece a otra intencion de pago");
            }
            completeIntent(activeIntent);
            return toResponse(cita);
        }
        if (cita.getMontoCentavos() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "CHARGE_REQUIRED", "Debes fijar el cargo antes de registrar pagos");
        }
        long paid = pagoRepository.sumPaidByCitaId(cita.getId());
        long balance = cita.getMontoCentavos() - paid;
        if (request.amount() > balance) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_EXCEEDS_BALANCE", "El pago excede el saldo pendiente");
        }
        try {
            PagoCita payment=pagoRepository.saveAndFlush(PagoCita.registrar(UUID.randomUUID().toString(), cita.getId(),
                    principal.accountId(), request.amount(), cita.getMoneda(), request.method(),
                    optional(request.reference()), request.idempotencyKey(), OffsetDateTime.now(clock)));
            audit.record(principal,"APPOINTMENT_PAYMENT_RECORDED","PAGO_CITA",payment.getId(),OffsetDateTime.now(clock));
        } catch (DataIntegrityViolationException duplicate) {
            PagoCita concurrent = pagoRepository.findByCitaIdAndIdempotencyKey(cita.getId(), request.idempotencyKey())
                    .orElseThrow(() -> duplicate);
            if (!sameIntent(concurrent, request)) {
                throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_IDEMPOTENCY_KEY_REUSED",
                        "La clave de idempotencia pertenece a otra intencion de pago");
            }
        }
        completeIntent(activeIntent);
        return toResponse(cita);
    }

    @Transactional(readOnly = true)
    public PaymentIntentResponse getPaymentIntent(StaffPrincipal principal) {
        requireReception(principal);
        return intentRepository.findById(principal.accountId()).map(this::toIntentResponse).orElse(null);
    }

    @Transactional
    public PaymentIntentResponse preparePaymentIntent(StaffPrincipal principal, String appointmentId,
                                                       RegisterPaymentRequest request) {
        requireReception(principal);
        IntencionPagoRecepcion current = intentRepository.findByCuentaIdForUpdate(principal.accountId()).orElse(null);
        if (current != null) {
            if (!sameIntent(current, appointmentId, request)) {
                throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_INTENT_ACTIVE",
                        "Existe una intencion de pago pendiente; debe resolverse antes de crear otra");
            }
            return toIntentResponse(current);
        }
        Cita cita = lockBillableAppointment(appointmentId);
        validatePayment(cita, request);
        IntencionPagoRecepcion created = IntencionPagoRecepcion.preparar(principal.accountId(), cita.getId(),
                request.amount(), request.method(), optional(request.reference()), request.idempotencyKey(),
                OffsetDateTime.now(clock));
        return toIntentResponse(intentRepository.saveAndFlush(created));
    }

    @Transactional
    public BillingAppointmentResponse commitPaymentIntent(StaffPrincipal principal) {
        requireReception(principal);
        IntencionPagoRecepcion intent = intentRepository.findByCuentaIdForUpdate(principal.accountId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "PAYMENT_INTENT_REQUIRED",
                        "No existe una intencion de pago preparada"));
        Cita cita = lockBillableAppointment(intent.getCitaId());
        RegisterPaymentRequest request = new RegisterPaymentRequest(intent.getMontoCentavos(), intent.getMetodo(),
                intent.getReferencia(), intent.getIdempotencyKey());
        PagoCita existing = pagoRepository.findByCitaIdAndIdempotencyKey(cita.getId(), intent.getIdempotencyKey())
                .orElse(null);
        if (existing == null) {
            validatePayment(cita, request);
            pagoRepository.saveAndFlush(PagoCita.registrar(UUID.randomUUID().toString(), cita.getId(),
                    principal.accountId(), request.amount(), cita.getMoneda(), request.method(),
                    optional(request.reference()), request.idempotencyKey(), OffsetDateTime.now(clock)));
        } else if (!sameIntent(existing, request)) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_IDEMPOTENCY_KEY_REUSED",
                    "La clave de idempotencia pertenece a otra intencion de pago");
        }
        if (intent.getEstado() != EstadoIntencionPago.COMPLETADA) {
            intent.completar(OffsetDateTime.now(clock));
            intentRepository.saveAndFlush(intent);
        }
        return toResponse(cita);
    }

    @Transactional
    public void acknowledgePaymentIntent(StaffPrincipal principal) {
        requireReception(principal);
        IntencionPagoRecepcion intent = intentRepository.findByCuentaIdForUpdate(principal.accountId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_INTENT_NOT_FOUND",
                        "No existe una intencion de pago pendiente"));
        PagoCita payment = pagoRepository.findByCitaIdAndIdempotencyKey(intent.getCitaId(), intent.getIdempotencyKey())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "PAYMENT_INTENT_UNRESOLVED",
                        "La intencion no tiene evidencia de pago persistido"));
        RegisterPaymentRequest request = new RegisterPaymentRequest(intent.getMontoCentavos(), intent.getMetodo(),
                intent.getReferencia(), intent.getIdempotencyKey());
        if (intent.getEstado() != EstadoIntencionPago.COMPLETADA || !sameIntent(payment, request)) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_INTENT_UNRESOLVED",
                    "La intencion no tiene evidencia de pago persistido");
        }
        intentRepository.delete(intent);
        intentRepository.flush();
    }

    private void validatePayment(Cita cita, RegisterPaymentRequest request) {
        if (cita.getMontoCentavos() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "CHARGE_REQUIRED", "Debes fijar el cargo antes de registrar pagos");
        }
        long balance = cita.getMontoCentavos() - pagoRepository.sumPaidByCitaId(cita.getId());
        if (request.amount() > balance) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_EXCEEDS_BALANCE", "El pago excede el saldo pendiente");
        }
    }

    private PaymentIntentResponse toIntentResponse(IntencionPagoRecepcion intent) {
        return new PaymentIntentResponse(intent.getCitaId(), intent.getMontoCentavos(), intent.getMetodo(),
                intent.getReferencia(), intent.getIdempotencyKey(), intent.getEstado(), intent.getCreadaEn(),
                intent.getCompletadaEn());
    }

    private void completeIntent(IntencionPagoRecepcion intent) {
        if (intent != null && intent.getEstado() != EstadoIntencionPago.COMPLETADA) {
            intent.completar(OffsetDateTime.now(clock));
            intentRepository.saveAndFlush(intent);
        }
    }

    private static boolean sameIntent(IntencionPagoRecepcion intent, String appointmentId,
                                      RegisterPaymentRequest request) {
        return intent.getCitaId().equals(appointmentId)
                && intent.getMontoCentavos().equals(request.amount())
                && intent.getMetodo() == request.method()
                && java.util.Objects.equals(intent.getReferencia(), optional(request.reference()))
                && intent.getIdempotencyKey().equals(request.idempotencyKey());
    }

    private Cita lockBillableAppointment(String appointmentId) {
        Cita cita = citaRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", "La cita no existe"));
        if (cita.getEstado() == EstadoCita.CANCELADA || cita.getEstado() != EstadoCita.COMPLETADA) {
            throw new ApiException(HttpStatus.CONFLICT, "APPOINTMENT_NOT_BILLABLE",
                    "Solo una cita atendida admite cobros");
        }
        if (!atencionRepository.existsByCitaId(cita.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "ATTENTION_REQUIRED",
                    "La cita debe tener una atención médica registrada antes del cobro");
        }
        return cita;
    }

    private Cita findAppointment(String appointmentId) {
        if (appointmentId == null || appointmentId.isBlank() || appointmentId.length() > 36) {
            throw new ApiException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", "La cita no existe");
        }
        return citaRepository.findById(appointmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", "La cita no existe"));
    }

    private BillingAppointmentResponse toResponse(Cita cita) {
        List<PaymentResponse> payments = pagoRepository.findByCitaIdOrderByRegistradoEnAsc(cita.getId())
                .stream().map(this::toPaymentResponse).toList();
        long paid = payments.stream().mapToLong(PaymentResponse::amount).sum();
        Long balance = cita.getMontoCentavos() == null ? null : cita.getMontoCentavos() - paid;
        return new BillingAppointmentResponse(cita.getId(), cita.getPacienteId(), cita.getMedico().getId(),
                cita.getEspecialidad().getId(), cita.getProgramadaEn(), cita.getEstado(),
                atencionRepository.existsByCitaId(cita.getId()), cita.getMontoCentavos(), cita.getMoneda(),
                paid, balance, cita.getMontoCentavos() != null, payments);
    }

    private PaymentResponse toPaymentResponse(PagoCita pago) {
        return new PaymentResponse(pago.getId(), pago.getCitaId(), pago.getRegistradoPorPersonalId(),
                pago.getMontoCentavos(), pago.getMoneda(), pago.getMetodo(), pago.getReferencia(),
                pago.getIdempotencyKey(), pago.getRegistradoEn());
    }

    private static boolean sameIntent(PagoCita pago, RegisterPaymentRequest request) {
        return pago.getMontoCentavos().equals(request.amount())
                && pago.getMetodo() == request.method()
                && java.util.Objects.equals(pago.getReferencia(), optional(request.reference()));
    }

    private static void requireReception(StaffPrincipal principal) {
        if (principal.role() != RolPersonal.RECEPCION) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No tienes permiso para esta operación");
        }
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
