package com.telemed.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import javax.imageio.ImageIO;

import com.github.benmanes.caffeine.cache.Cache;
import com.telemed.platform.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CaptchaServiceTest {

    @Test
    void createsAnImageAndAcceptsTheAnswerOnceRegardlessOfCase() throws Exception {
        CaptchaService service = new CaptchaService();
        var challenge = service.create();
        @SuppressWarnings("unchecked")
        Cache<String, String> answers = (Cache<String, String>) ReflectionTestUtils.getField(service, "answers");
        String answer = answers.getIfPresent(challenge.captchaId());

        assertThat(challenge.image()).startsWith("data:image/png;base64,");
        assertThat(answer).matches("[ABCDEFGHJKMNPRSTUVWXYZ]{6}");
        byte[] imageBytes = Base64.getDecoder().decode(challenge.image().substring("data:image/png;base64,".length()));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        assertThat(image.getWidth()).isEqualTo(200);
        assertThat(image.getHeight()).isEqualTo(70);

        service.verify(challenge.captchaId(), answer.toLowerCase(java.util.Locale.ROOT));

        assertThatThrownBy(() -> service.verify(challenge.captchaId(), answer))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("incorrect ou expiré");
    }

    @Test
    void consumesCaptchaAfterAnIncorrectAttempt() {
        CaptchaService service = new CaptchaService();
        var challenge = service.create();
        @SuppressWarnings("unchecked")
        Cache<String, String> answers = (Cache<String, String>) ReflectionTestUtils.getField(service, "answers");
        String answer = answers.getIfPresent(challenge.captchaId());

        assertThatThrownBy(() -> service.verify(challenge.captchaId(), "!!!!!!"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.verify(challenge.captchaId(), answer))
                .isInstanceOf(InvalidRequestException.class);
    }
}
