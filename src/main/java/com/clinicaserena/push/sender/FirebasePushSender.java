package com.clinicaserena.push.sender;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Emisor FCM. Nunca escribe en logs el token, el mensaje ni el texto de excepciones de Firebase. */
@Component
@ConditionalOnProperty(name = "clinica.push.provider", havingValue = "FIREBASE")
public class FirebasePushSender implements PushSender {

    private final FirebaseMessaging firebase;

    public FirebasePushSender(FirebaseMessaging firebase) {
        this.firebase = firebase;
    }

    @Override
    public PushDeliveryResult send(PushTarget target, PushMessage message) {
        Message fcmMessage = Message.builder()
                .setToken(target.token())
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(message.data())
                .build();
        try {
            firebase.send(fcmMessage);
            return PushDeliveryResult.delivered();
        } catch (FirebaseMessagingException exception) {
            if (exception.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                return PushDeliveryResult.invalidToken();
            }
            MessagingErrorCode code = exception.getMessagingErrorCode();
            return PushDeliveryResult.failed(code == null ? "FIREBASE_ERROR" : "FCM_" + code.name());
        }
    }
}
