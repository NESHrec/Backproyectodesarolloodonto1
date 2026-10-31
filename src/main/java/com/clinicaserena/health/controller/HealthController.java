package com.clinicaserena.health.controller;

import com.clinicaserena.health.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private static final String SERVICE_STATUS_UP = "UP";
    private static final String SERVICE_NAME = "clinica-serena-api";

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse(SERVICE_STATUS_UP, SERVICE_NAME);
    }
}
