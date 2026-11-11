package com.rental.platform.service;

import org.junit.jupiter.api.Test;
import java.security.SecureRandom;
import static org.junit.jupiter.api.Assertions.*;

class ImageCaptchaServiceTest {
    private ImageCaptchaService service() {
        return new ImageCaptchaService(new SecureRandom() {
            @Override public int nextInt(int bound) { return 0; }
        });
    }
    @Test void imageHidesAnswerAndVerificationIsCaseInsensitive() {
        ImageCaptchaService service = service();
        var challenge = service.create();
        assertEquals(2, challenge.size());
        assertTrue(challenge.get("image").startsWith("data:image/png;base64,"));
        String ticket = service.verify(challenge.get("id"), "aaaaa", "13800000000");
        assertNotNull(ticket);
        assertTrue(service.consume(ticket, "13800000000"));
        assertFalse(service.consume(ticket, "13800000000"));
    }
    @Test void wrongAnswerConsumesChallengeAndPhoneCannotBeChanged() {
        ImageCaptchaService service = service();
        var challenge = service.create();
        assertNull(service.verify(challenge.get("id"), "WRONG", "13800000000"));
        assertNull(service.verify(challenge.get("id"), "AAAAA", "13800000000"));
        String ticket = service.verify(service.create().get("id"), "AAAAA", "13800000000");
        assertFalse(service.consume(ticket, "13900000000"));
        assertFalse(service.consume(ticket, "13800000000"));
    }
    @Test void missingOrUnknownPermitsCannotSend() {
        ImageCaptchaService service = service();
        assertFalse(service.consume(null, "13800000000"));
        assertFalse(service.consume("unknown", "13800000000"));
        assertNull(service.verify(null, "AAAAA", "13800000000"));
    }
}
//123
