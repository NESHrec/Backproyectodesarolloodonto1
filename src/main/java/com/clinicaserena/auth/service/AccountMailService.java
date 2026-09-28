package com.clinicaserena.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class AccountMailService {
    private final JavaMailSender sender; private final String from; private final String publicUrl;
    public AccountMailService(JavaMailSender sender, @Value("${clinica.auth.mail-from}") String from,
            @Value("${clinica.auth.public-web-url}") String publicUrl) {
        this.sender=sender; this.from=from; this.publicUrl=publicUrl.replaceAll("/+$","");
    }
    public void verification(String to, String token) { send(to,"Verifica tu correo de Clínica Serena",
            "Abre este enlace para verificar tu cuenta:\n"+publicUrl+"/verificar-correo#token="+token); }
    public void recovery(String to, String token) { send(to,"Restablece tu contraseña de Clínica Serena",
            "Abre este enlace para restablecer tu contraseña:\n"+publicUrl+"/restablecer-contrasena#token="+token); }
    private void send(String to,String subject,String body) { SimpleMailMessage m=new SimpleMailMessage();
        m.setFrom(from);m.setTo(to);m.setSubject(subject);m.setText(body);sender.send(m); }
}
