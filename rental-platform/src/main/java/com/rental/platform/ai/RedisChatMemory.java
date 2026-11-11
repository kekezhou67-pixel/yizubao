package com.rental.platform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis 版对话记忆（AI 改造 · 阶段2）
 * 依据：讲义02 第9章 RedisChatMemory + 工程方案 §8.1
 *
 * Key 设计：chat:memory:{conversationId}，List 结构，每条是 {role, content} 的 JSON
 * - add:   rightPushAll + LTRIM 保留最近 40 条 + TTL 7 天
 * - get:   range(0,-1)，按 role 还原成具体 Message 类型
 * - clear: delete
 *
 * 关键坑1（工程方案 §2.2）：必须用 StringRedisTemplate + 显式 Jackson ——
 *   Map.of() 返回 final 类，Jackson 的 NON_FINAL 类型信息策略不写 @class，
 *   RedisTemplate&lt;String,Object&gt; 默认序列化读不回来。
 * 关键坑2：get() 必须按 role 还原 UserMessage/AssistantMessage/SystemMessage ——
 *   如果全部还原成 UserMessage，模型收到的角色全错，多轮对话会崩。
 */
@Component
public class RedisChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemory.class);

    private static final String KEY_PREFIX = "chat:memory:";
    private static final int MAX_MESSAGES = 40;
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisChatMemory(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (conversationId == null || messages == null || messages.isEmpty()) {
            return;
        }
        String key = KEY_PREFIX + conversationId;
        List<String> jsons = new ArrayList<>();
        for (Message m : messages) {
            try {
                // 用 HashMap 而不是 Map.of()：Map.of() 是 final 类，见类注释坑1
                Map<String, String> simple = new HashMap<>();
                simple.put("role", m.getMessageType().name());
                simple.put("content", m.getText() != null ? m.getText() : "");
                jsons.add(mapper.writeValueAsString(simple));
            } catch (Exception e) {
                log.error("序列化对话记忆失败: {}", e.getMessage());
            }
        }
        if (jsons.isEmpty()) {
            return;
        }
        redis.opsForList().rightPushAll(key, jsons.toArray(new String[0]));
        // 只保留最近 MAX_MESSAGES 条，防止超上下文窗口 + 成本失控（讲义02：历史必须剪裁）
        redis.opsForList().trim(key, -MAX_MESSAGES, -1);
        redis.expire(key, TTL);
    }

    @Override
    public List<Message> get(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        List<String> jsons = redis.opsForList().range(key, 0, -1);
        if (jsons == null || jsons.isEmpty()) {
            return List.of();
        }
        List<Message> messages = new ArrayList<>();
        for (String json : jsons) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> m = mapper.readValue(json, Map.class);
                String role = m.getOrDefault("role", "USER");
                String content = m.getOrDefault("content", "");
                // ★ 按 role 还原具体类型（见类注释坑2）
                switch (role) {
                    case "ASSISTANT" -> messages.add(new AssistantMessage(content));
                    case "SYSTEM" -> messages.add(new SystemMessage(content));
                    default -> messages.add(new UserMessage(content));
                }
            } catch (Exception e) {
                log.warn("跳过损坏的记忆记录: {}", json);
            }
        }
        return messages;
    }

    @Override
    public void clear(String conversationId) {
        redis.delete(KEY_PREFIX + conversationId);
    }
}
