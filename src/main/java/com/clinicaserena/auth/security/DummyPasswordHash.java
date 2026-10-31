package com.clinicaserena.auth.security;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Creates a process-local password hash used only to keep unknown-account login
 * attempts performing one password-hash verification, without embedding a
 * credential-like hash in source control.
 */
public final class DummyPasswordHash {

    private static final SecureRandom RANDOM = new SecureRandom();

    private DummyPasswordHash() {
    }

    public static String create(PasswordEncoder passwordEncoder) {
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        String randomPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return passwordEncoder.encode(randomPassword);
    }
}
