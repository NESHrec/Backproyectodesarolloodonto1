package com.clinicaserena.push;

import com.clinicaserena.push.sender.FirebasePushSender;
import com.clinicaserena.push.sender.PushDeliveryResult;
import com.clinicaserena.push.sender.PushMessage;
import com.clinicaserena.push.sender.PushTarget;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class FirebasePushSenderTest {

    @Test
    void enviaMensajeFcmAlTokenConElContenidoEsperado() throws Exception {
        FirebaseMessaging firebase = mock(FirebaseMessaging.class);
        when(firebase.send(any(Message.class))).thenReturn("projects/test/messages/id");
        FirebasePushSender sender = new FirebasePushSender(firebase);

        PushDeliveryResult result = sender.send(
                new PushTarget("device-id", "registration-token-not-for-production-123456"),
                new PushMessage("Recordatorio de cita", "Tienes una cita mañana.",
                        Map.of("type", "APPOINTMENT_REMINDER", "appointmentId", "appointment-id")));

        verify(firebase).send(any(Message.class));
        assertThat(result.outcome()).isEqualTo(PushDeliveryResult.Outcome.DELIVERED);
    }

    @Test
    void unTokenNoRegistradoSeMarcaParaRetiro() throws Exception {
        FirebaseMessaging firebase = mock(FirebaseMessaging.class);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(firebase.send(any(Message.class))).thenThrow(exception);

        PushDeliveryResult result = new FirebasePushSender(firebase).send(
                new PushTarget("device-id", "registration-token-not-for-production-123456"),
                new PushMessage("Recordatorio de cita", "Tienes una cita mañana.", Map.of("type", "APPOINTMENT_REMINDER")));

        assertThat(result.outcome()).isEqualTo(PushDeliveryResult.Outcome.INVALID_TOKEN);
    }
}
