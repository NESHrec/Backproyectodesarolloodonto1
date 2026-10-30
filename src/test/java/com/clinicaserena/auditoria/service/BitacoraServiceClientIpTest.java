package com.clinicaserena.auditoria.service;

import com.clinicaserena.auditoria.entity.BitacoraEvento;
import com.clinicaserena.auditoria.repository.BitacoraEventoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class BitacoraServiceClientIpTest {

    @Test
    void registraDireccionIpv4Resuelta() {
        assertThat(recordedIp("203.0.113.17")).isEqualTo("203.0.113.17");
    }

    @Test
    void registraDireccionIpv6Resuelta() {
        assertThat(recordedIp("2001:db8::1")).isEqualTo("2001:db8::1");
    }

    @Test
    void registraValorSeguroCuandoLaDireccionNoEstaDisponible() {
        assertThat(recordedIp(null)).isEqualTo("unknown");
    }

    private static String recordedIp(String remoteAddress) {
        BitacoraEventoRepository repository = mock(BitacoraEventoRepository.class);
        BitacoraService service = new BitacoraService(repository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);

        service.recordSecurityEvent(null, "HTTP_401", request);

        ArgumentCaptor<BitacoraEvento> event = ArgumentCaptor.forClass(BitacoraEvento.class);
        verify(repository).saveAndFlush(event.capture());
        return event.getValue().getClientIp();
    }
}
