package com.clinicaserena.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PrometheusEndpointIntegrationTest {

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void exposesPrometheusScrapeOnPrivateManagementPortWithoutApiCredentials() {
        var response = restTemplate.getForEntity(
                "http://127.0.0.1:" + managementPort + "/actuator/prometheus",
                String.class);

        assertThat(response.getStatusCode())
                .as("Actuator response body: %s", response.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("# HELP", "jvm_memory_used_bytes");
    }
}
