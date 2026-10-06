package com.clinicaserena.pagos.controller;

import com.clinicaserena.pagos.dto.BillingAppointmentResponse;
import com.clinicaserena.pagos.dto.RegisterPaymentRequest;
import com.clinicaserena.pagos.dto.PaymentIntentResponse;
import com.clinicaserena.pagos.dto.PaymentIntentStatusResponse;
import com.clinicaserena.pagos.dto.SetAppointmentChargeRequest;
import com.clinicaserena.pagos.service.ReceptionBillingService;
import com.clinicaserena.staff.security.StaffPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staff/billing")
public class ReceptionBillingController {

    private final ReceptionBillingService service;

    public ReceptionBillingController(ReceptionBillingService service) {
        this.service = service;
    }

    @GetMapping("/appointments")
    public ResponseEntity<List<BillingAppointmentResponse>> list(@RequestParam(defaultValue = "50") int limit,
                                                                 Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.list(principal(authentication), limit));
    }

    @GetMapping("/appointments/{appointmentId}")
    public ResponseEntity<BillingAppointmentResponse> get(@PathVariable String appointmentId,
                                                          Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.get(principal(authentication), appointmentId));
    }

    @PutMapping("/appointments/{appointmentId}/charge")
    public ResponseEntity<BillingAppointmentResponse> setCharge(@PathVariable String appointmentId,
                                                                @Valid @RequestBody SetAppointmentChargeRequest request,
                                                                Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.setCharge(principal(authentication), appointmentId, request));
    }

    @PostMapping("/appointments/{appointmentId}/payments")
    public ResponseEntity<BillingAppointmentResponse> registerPayment(@PathVariable String appointmentId,
                                                                      @Valid @RequestBody RegisterPaymentRequest request,
                                                                      Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.registerPayment(principal(authentication), appointmentId, request));
    }

    @GetMapping("/payment-intent")
    public ResponseEntity<PaymentIntentStatusResponse> getPaymentIntent(Authentication authentication) {
        PaymentIntentResponse intent = service.getPaymentIntent(principal(authentication));
        PaymentIntentStatusResponse status = intent == null
                ? PaymentIntentStatusResponse.absent()
                : PaymentIntentStatusResponse.active(intent);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(status);
    }

    @PutMapping("/appointments/{appointmentId}/payment-intent")
    public ResponseEntity<PaymentIntentResponse> preparePaymentIntent(@PathVariable String appointmentId,
                                                                       @Valid @RequestBody RegisterPaymentRequest request,
                                                                       Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.preparePaymentIntent(principal(authentication), appointmentId, request));
    }

    @PostMapping("/payment-intent/commit")
    public ResponseEntity<BillingAppointmentResponse> commitPaymentIntent(Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.commitPaymentIntent(principal(authentication)));
    }

    @DeleteMapping("/payment-intent")
    public ResponseEntity<Void> acknowledgePaymentIntent(Authentication authentication) {
        service.acknowledgePaymentIntent(principal(authentication));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static StaffPrincipal principal(Authentication authentication) {
        return (StaffPrincipal) authentication.getPrincipal();
    }
}
