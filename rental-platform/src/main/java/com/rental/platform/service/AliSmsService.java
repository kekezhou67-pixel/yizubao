package com.rental.platform.service;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.teaopenapi.models.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * 阿里云短信服务 —— 使用 AK/SK + 官方 SDK
 */
@Service
public class AliSmsService {

    private static final Logger log = LoggerFactory.getLogger(AliSmsService.class);

    @Value("${aliyun.sms.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.sms.access-key-secret:}")
    private String accessKeySecret;

    @Value("${aliyun.sms.sign-name:恒创联众}")
    private String signName;

    @Value("${aliyun.sms.template-code:100001}")
    private String templateCode;

    private Client client;
    private boolean enabled;

    @PostConstruct
    public void init() {
        if (accessKeyId == null || accessKeyId.isBlank() || accessKeySecret == null || accessKeySecret.isBlank()) {
            log.warn("阿里云短信 AK/SK 未配置");
            this.enabled = false;
            return;
        }
        try {
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret)
                    .setEndpoint("dypnsapi.aliyuncs.com");
            this.client = new Client(config);
            this.enabled = true;
            log.info("阿里云短信 AK/SK 客户端初始化成功");
        } catch (Exception e) {
            log.error("阿里云短信初始化失败: {}", e.getMessage());
            this.enabled = false;
        }
    }

    public boolean sendCode(String phone, String code) {
        if (!enabled || client == null) return false;
        try {
            // 模板 100001 需要 code + min 两个变量
            String templateParam = "{\"code\":\"" + code + "\",\"min\":\"5\"}";

            SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                    .setPhoneNumber(phone)
                    .setSignName(signName)
                    .setTemplateCode(templateCode)
                    .setTemplateParam(templateParam);

            SendSmsVerifyCodeResponse response = client.sendSmsVerifyCode(request);
            String respCode = response.getBody() != null ? response.getBody().getCode() : "null";
            String respMsg = response.getBody() != null ? response.getBody().getMessage() : "";

            if ("OK".equals(respCode)) {
                log.info("[短信] 发送成功, phone={}", maskPhone(phone));
                return true;
            } else {
                log.warn("[短信] 发送失败, phone={}, code={}, message={}",
                        maskPhone(phone), respCode, respMsg);
                return false;
            }
        } catch (Exception e) {
            log.error("[短信] 异常, phone={}, error={}", maskPhone(phone), e.getMessage());
            return false;
        }
    }

    public boolean sendNotification(String phone, String message) { return false; }

    private String maskPhone(String phone) {
        if (phone != null && phone.length() == 11) return phone.substring(0, 3) + "****" + phone.substring(7);
        return phone;
    }
}
