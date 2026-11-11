package com.rental.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 生产环境配置校验器。
 *
 * <h3>为什么需要它？</h3>
 * 生产环境的数据库地址、账号、密码必须从环境变量注入，配置文件里写成
 * {@code spring.datasource.url=${DB_URL}}。
 *
 * <p>但这里有个 Spring Boot 的坑：
 * <b>@ConfigurationProperties 绑定（spring.datasource.* 走的就是这条）遇到未解析的占位符时，
 * 不会抛异常，而是把 "${DB_URL}" 原样当成字符串值。</b>
 * 结果是应用能启动，直到第一次访问数据库才报
 * "Driver claims to not accept jdbcUrl, ${DB_URL}" —— 这就是"带病运行"。
 *
 * <p>所以这里在<b>容器初始化阶段</b>（InitializingBean.afterPropertiesSet）显式检查，
 * 缺配置直接让启动失败（fail fast）。
 *
 * <h3>为什么用 @Profile("prod")</h3>
 * 只在生产环境校验。开发环境有默认值，不需要强制。
 */
@Component
@Profile("prod")
public class ProdConfigValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ProdConfigValidator.class);

    /** 生产环境必须提供的环境变量（AI 改造后新增 DEEPSEEK_API_KEY / REDIS_HOST） */
    private static final List<String> REQUIRED_ENV_VARS = List.of(
            "DB_URL",
            "DB_USERNAME",
            "DB_PASSWORD",
            "JWT_SECRET",
            "DEEPSEEK_API_KEY",   // AI 功能核心：缺失则所有 AI 接口不可用，必须 fail-fast
            "REDIS_HOST"          // 对话记忆/Token统计依赖
    );

    private final Environment environment;

    public ProdConfigValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> missing = new ArrayList<>();
        for (String key : REQUIRED_ENV_VARS) {
            // ★ 必须用 getProperty(key, null) 或 try-catch：
            //   environment.getProperty("spring.datasource.url") 会触发占位符解析，
            //   遇到未定义的 ${DB_URL} 会直接抛 PlaceholderResolutionException，
            //   那样就看不到我们下面友好的错误列表了。
            String value;
            try {
                value = environment.getProperty(key);
            } catch (IllegalArgumentException ex) {
                // 占位符解析失败 = 环境变量没配
                value = null;
            }
            if (value == null || value.isBlank()) {
                missing.add(key);
            }
        }

        // 额外检查：datasource url 是否被解析成了字面量占位符
        try {
            String dsUrl = environment.getProperty("spring.datasource.url");
            if (dsUrl != null && dsUrl.startsWith("${")) {
                missing.add("spring.datasource.url（占位符未被解析：" + dsUrl + "）");
            }
        } catch (IllegalArgumentException ex) {
            // 解析失败已经体现在 missing 列表里了，这里忽略
        }

        if (!missing.isEmpty()) {
            String msg = String.join("\n  - ", missing);
            throw new IllegalStateException(
                    "生产环境缺少必填环境变量，拒绝启动（fail fast）：\n  - " + msg
                    + "\n\n请在启动前设置这些环境变量，例如：\n"
                    + "  set DB_URL=jdbc:mysql://your-db:3306/rental_db\n"
                    + "  set DB_USERNAME=rental_user\n"
                    + "  set DB_PASSWORD=your-password\n"
                    + "  set JWT_SECRET=at-least-32-bytes-long-production-secret");
        }

        log.info("生产环境配置校验通过：DB_URL / DB_USERNAME / DB_PASSWORD / JWT_SECRET / DEEPSEEK_API_KEY / REDIS_HOST 均已设置");
    }
}
