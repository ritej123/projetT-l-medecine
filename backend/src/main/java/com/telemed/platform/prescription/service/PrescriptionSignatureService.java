package com.telemed.platform.prescription.service;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.telemed.platform.common.exception.ServiceUnavailableException;
import com.telemed.platform.common.security.EphemeralSecret;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PrescriptionSignatureService {

    private final String signingSecret;

    @Autowired
    public PrescriptionSignatureService(
            @Value("${app.prescription.signing-secret:}") String signingSecret,
            Environment environment) {
        this(signingSecret, environment.acceptsProfiles(Profiles.of("dev")));
    }

    public PrescriptionSignatureService(String signingSecret) {
        this(signingSecret, false);
    }

    private PrescriptionSignatureService(String signingSecret, boolean developmentProfile) {
        this.signingSecret = StringUtils.hasText(signingSecret)
                ? signingSecret
                : developmentProfile ? EphemeralSecret.generate() : "";
        if (StringUtils.hasText(this.signingSecret)
                && this.signingSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("PRESCRIPTION_SIGNING_SECRET must contain at least 32 bytes.");
        }
        if (!developmentProfile && !StringUtils.hasText(this.signingSecret)) {
            throw new IllegalStateException("PRESCRIPTION_SIGNING_SECRET must be configured outside the dev profile.");
        }
    }

    public String sign(UUID prescriptionId, UUID doctorUserId, String signedAt, String contentSha256) {
        if (!StringUtils.hasText(signingSecret)
                || signingSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new ServiceUnavailableException(
                    "La clé de signature des ordonnances doit contenir au moins 32 octets et être configurée.");
        }
        String payload = prescriptionId + "|" + doctorUserId + "|" + signedAt + "|" + contentSha256;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable.", exception);
        }
    }

    public boolean verify(
            UUID prescriptionId,
            UUID doctorUserId,
            String signedAt,
            String contentSha256,
            String signature) {
        if (!StringUtils.hasText(signature)) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                sign(prescriptionId, doctorUserId, signedAt, contentSha256)
                        .getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII));
    }
}
