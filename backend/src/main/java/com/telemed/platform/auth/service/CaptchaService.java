package com.telemed.platform.auth.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import javax.imageio.ImageIO;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.google.code.kaptcha.util.Config;
import com.telemed.platform.auth.api.CaptchaResponse;
import com.telemed.platform.common.exception.InvalidRequestException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CaptchaService {

    private static final String ALLOWED_CHARACTERS = "ABCDEFGHJKMNPRSTUVWXYZ";
    private static final int CAPTCHA_LENGTH = 6;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Cache<String, String> answers = Caffeine.newBuilder()
            .maximumSize(20_000)
            .expireAfterWrite(java.time.Duration.ofMinutes(2))
            .build();
    private final DefaultKaptcha imageProducer;

    public CaptchaService() {
        Properties properties = new Properties();
        properties.setProperty("kaptcha.border", "no");
        properties.setProperty("kaptcha.image.width", "200");
        properties.setProperty("kaptcha.image.height", "70");
        properties.setProperty("kaptcha.textproducer.font.size", "36");
        properties.setProperty("kaptcha.textproducer.font.names", "Arial,Courier");
        properties.setProperty("kaptcha.textproducer.font.color", "blue");
        properties.setProperty("kaptcha.textproducer.char.space", "3");
        properties.setProperty("kaptcha.word.impl", CaptchaWordRenderer.class.getName());
        properties.setProperty("kaptcha.noise.color", "gray");
        properties.setProperty("kaptcha.noise.impl", "com.google.code.kaptcha.impl.DefaultNoise");
        properties.setProperty("kaptcha.obscurificator.impl", "com.google.code.kaptcha.impl.WaterRipple");
        properties.setProperty("kaptcha.background.impl", "com.google.code.kaptcha.impl.DefaultBackground");
        imageProducer = new DefaultKaptcha();
        imageProducer.setConfig(new Config(properties));
    }

    public CaptchaResponse create() {
        String answer = generateAnswer();
        String captchaId = UUID.randomUUID().toString();
        try {
            BufferedImage image = imageProducer.createImage(answer);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output)) {
                throw new IOException("Aucun encodeur PNG n’est disponible.");
            }
            String imageData = Base64.getEncoder().encodeToString(output.toByteArray());
            answers.put(captchaId, answer);
            return new CaptchaResponse(captchaId, "data:image/png;base64," + imageData);
        } catch (IOException exception) {
            throw new IllegalStateException("L’image CAPTCHA n’a pas pu être créée.", exception);
        }
    }

    public void verify(String captchaId, String presentedAnswer) {
        String expectedAnswer = StringUtils.hasText(captchaId)
                ? answers.asMap().remove(captchaId)
                : null;
        String normalizedAnswer = StringUtils.hasText(presentedAnswer)
                ? presentedAnswer.trim().toUpperCase(Locale.ROOT)
                : "";
        if (expectedAnswer == null || !MessageDigest.isEqual(
                expectedAnswer.getBytes(StandardCharsets.US_ASCII),
                normalizedAnswer.getBytes(StandardCharsets.US_ASCII))) {
            throw new InvalidRequestException("Le code CAPTCHA est incorrect ou expiré.");
        }
    }

    private String generateAnswer() {
        StringBuilder answer = new StringBuilder(CAPTCHA_LENGTH);
        for (int index = 0; index < CAPTCHA_LENGTH; index++) {
            answer.append(ALLOWED_CHARACTERS.charAt(secureRandom.nextInt(ALLOWED_CHARACTERS.length())));
        }
        return answer.toString();
    }
}
