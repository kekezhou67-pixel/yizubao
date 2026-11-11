package com.rental.platform.service;

import org.springframework.stereotype.Service;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/** Short-lived image challenges and one-use, phone-bound SMS permits. */
@Service
public class ImageCaptchaService {
    private final SecureRandom random;
    public ImageCaptchaService() { this(new SecureRandom()); }
    ImageCaptchaService(SecureRandom random) { this.random = random; }
    private final Map<String, Entry> challenges = new ConcurrentHashMap<>();
    private final Map<String, Entry> permits = new ConcurrentHashMap<>();
    private record Entry(String value, long expires) {}
    public synchronized Map<String, String> create() {
        long now = System.currentTimeMillis();
        challenges.entrySet().removeIf(e -> e.getValue().expires < now);
        permits.entrySet().removeIf(e -> e.getValue().expires < now);
        if (challenges.size() >= 5000) throw new IllegalStateException("请求较多，请稍后重试");
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder answer = new StringBuilder();
        BufferedImage image = new BufferedImage(164, 52, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(235, 244, 239)); g.fillRect(0, 0, 164, 52);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = 0; i < 7; i++) {
                g.setColor(new Color(130 + random.nextInt(70), 180, 165));
                g.drawLine(random.nextInt(164), random.nextInt(52), random.nextInt(164), random.nextInt(52));
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            for (int i = 0; i < 5; i++) {
                char c = alphabet.charAt(random.nextInt(alphabet.length())); answer.append(c);
                g.setColor(new Color(20 + random.nextInt(50), 90 + random.nextInt(40), 75));
                g.drawString(String.valueOf(c), 10 + i * 29, 35 + random.nextInt(7));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out);
            String id = UUID.randomUUID().toString();
            challenges.put(id, new Entry(answer.toString(), now + 120000));
            return Map.of("id", id, "image", "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray()));
        } catch (java.io.IOException e) { throw new IllegalStateException("图片加载失败，请重试"); }
        finally { g.dispose(); }
    }
    public synchronized String verify(String id, String answer, String phone) {
        Entry entry = id == null ? null : challenges.remove(id);
        if (entry == null || entry.expires < System.currentTimeMillis() || answer == null || !entry.value.equalsIgnoreCase(answer.trim())) return null;
        String ticket = UUID.randomUUID().toString();
        permits.put(ticket, new Entry(phone, System.currentTimeMillis() + 120000));
        return ticket;
    }
    public synchronized boolean consume(String ticket, String phone) {
        Entry entry = ticket == null ? null : permits.remove(ticket);
        return entry != null && entry.expires >= System.currentTimeMillis() && entry.value.equals(phone);
    }
}
