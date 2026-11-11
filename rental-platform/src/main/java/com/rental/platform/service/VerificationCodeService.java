package com.rental.platform.service;

import com.rental.platform.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短信/邮箱验证码服务 —— 含发送频率限制（60秒内不能重复发送）
 * 手机号 → 阿里云短信；邮箱 → 控制台打印（可后续接入 SMTP）
 */
@Service
public class VerificationCodeService {

    private static final Logger log = LoggerFactory.getLogger(VerificationCodeService.class);

    private static final int CODE_LENGTH = 6;
    private static final long EXPIRE_MS = 5 * 60 * 1000;
    private static final long COOLDOWN_MS = 60 * 1000;    // 发送间隔：60秒

    private final Map<String, CodeEntry> codeMap = new ConcurrentHashMap<>();
    private final Map<String, Long> sendTimeMap = new ConcurrentHashMap<>();  // 上次发送时间
    private final SecureRandom random = new SecureRandom();

    @Autowired(required = false)
    private AliSmsService aliSmsService;

    /**
     * 生成验证码并发送（含频率限制）
     * @param target 手机号或邮箱
     * @throws BusinessException 如果60秒内重复发送
     */
    public synchronized String generateCode(String target) {
        // 频率限制检查
        Long lastSend = sendTimeMap.get(target);
        if (lastSend != null) {
            long elapsed = System.currentTimeMillis() - lastSend;
            if (elapsed < COOLDOWN_MS) {
                long remaining = (COOLDOWN_MS - elapsed) / 1000;
                throw new BusinessException("操作太频繁，请" + remaining + "秒后再试");
            }
        }

        // 生成6位数字验证码
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        String code = sb.toString();
        if (!isPhone(target) || aliSmsService == null || !aliSmsService.sendCode(target, code)) {
            throw new BusinessException("短信暂时发送失败，请稍后重试或使用账号密码登录");
        }
        codeMap.put(target, new CodeEntry(code, System.currentTimeMillis() + EXPIRE_MS));
        sendTimeMap.put(target, System.currentTimeMillis());
        log.info("[短信] 验证码已发送至 {}", maskTarget(target));

        return code;
    }

    public synchronized boolean verify(String target, String code) {
        CodeEntry entry = codeMap.get(target);
        if (entry == null) return false;
        if (System.currentTimeMillis() > entry.expireAt) {
            codeMap.remove(target);
            return false;
        }
        if (entry.code.equals(code)) {
            codeMap.remove(target);
            sendTimeMap.remove(target);  // 验证成功后清除冷却期，允许立即重新发送
            return true;
        }
        return false;
    }

    private boolean isPhone(String target) {
        return target != null && target.matches("^1[3-9]\\d{9}$");
    }

    /** 脱敏显示手机号：138****1234 */
    private String maskTarget(String target) {
        if (isPhone(target) && target.length() == 11) {
            return target.substring(0, 3) + "****" + target.substring(7);
        }
        return target;
    }

    /** 内部记录：验证码 + 过期时间 */
    private record CodeEntry(String code, long expireAt) {}
}
