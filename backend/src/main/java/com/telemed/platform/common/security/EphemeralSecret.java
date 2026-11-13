package com.telemed.platform.common.security;

import java.security.SecureRandom;
import java.util.Base64;

public final class EphemeralSecret {

    private static final SecureRandom RANDOM = new SecureRandom();

    private EphemeralSecret() {}

    public static String generate() {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        return Base64.getEncoder().encodeToString(secret);
    }
}
