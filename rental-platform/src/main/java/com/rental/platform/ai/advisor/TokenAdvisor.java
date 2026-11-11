package com.rental.platform.ai.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Token 统计 Advisor（AI 改造 · 阶段2）
 * 依据：讲义02 第8章 —— Advisor = 大模型调用的拦截器（类比 Filter/Interceptor），
 * 在 chain.nextCall() 之前可以改请求，之后可以读响应。
 *
 * 统计两级：
 * 1. 进程内 AtomicLong —— 全局累计（重启清零），/api/ai/token/stats 直接读
 * 2. Redis Hash token:usage:{userId} —— 按用户持久累计（input/output/calls 三个字段）
 *
 * userId 来源：Controller 调 .advisors(a -> a.param(TokenAdvisor.USER_ID_PARAM, userId))，
 * 没传就记到 "anonymous" 名下。
 *
 * 流式注意：DeepSeek 流式响应每个 chunk 都带累计 usage，
 * 只在流结束时取最后一个非空 usage 记一次账，避免逐 chunk 重复累加。
 */
@Component
public class TokenAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TokenAdvisor.class);

    /** Controller 通过 advisor param 传 userId 用的键 */
    public static final String USER_ID_PARAM = "token_user_id";
    private static final String KEY_PREFIX = "token:usage:";

    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();
    private final AtomicLong callCount = new AtomicLong();

    private final StringRedisTemplate redis;

    public TokenAdvisor(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // ==================== 同步调用 ====================

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        if (response != null && response.chatResponse() != null
                && response.chatResponse().getMetadata() != null) {
            recordUsage(request, response.chatResponse().getMetadata().getUsage());
        }
        return response;
    }

    // ==================== 流式调用 ====================

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        AtomicReference<Usage> lastUsage = new AtomicReference<>();
        return chain.nextStream(request)
                .doOnNext(resp -> {
                    if (resp != null && resp.chatResponse() != null
                            && resp.chatResponse().getMetadata() != null) {
                        Usage u = resp.chatResponse().getMetadata().getUsage();
                        // DeepSeek 流式 usage 是累计值，留最后一个非空的即可
                        if (u != null && u.getTotalTokens() != null && u.getTotalTokens() > 0) {
                            lastUsage.set(u);
                        }
                    }
                })
                .doOnComplete(() -> recordUsage(request, lastUsage.get()))
                .doOnError(e -> log.warn("流式调用出错，Token 统计跳过: {}", e.getMessage()));
    }

    // ==================== 记账 ====================

    private void recordUsage(ChatClientRequest request, Usage usage) {
        if (usage == null) {
            return;
        }
        long in = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0L;
        long out = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0L;
        if (in == 0 && out == 0) {
            return;
        }
        // 1. 进程内全局累计
        inputTokens.addAndGet(in);
        outputTokens.addAndGet(out);
        callCount.incrementAndGet();

        // 2. Redis 按用户累计（Hash: input/output/calls）
        Object uid = request.context() != null ? request.context().get(USER_ID_PARAM) : null;
        String userKey = KEY_PREFIX + (uid != null ? uid.toString() : "anonymous");
        try {
            redis.opsForHash().increment(userKey, "input", in);
            redis.opsForHash().increment(userKey, "output", out);
            redis.opsForHash().increment(userKey, "calls", 1);
        } catch (Exception e) {
            // Redis 挂了不能影响对话主流程
            log.warn("Token 统计写 Redis 失败（不影响对话）: {}", e.getMessage());
        }
    }

    // ==================== 查询接口 ====================

    public long getInputTokens() {
        return inputTokens.get();
    }

    public long getOutputTokens() {
        return outputTokens.get();
    }

    public long getCallCount() {
        return callCount.get();
    }

    /** 读某用户的 Redis 累计（可能为 null） */
    public Map<String, Object> getUserStats(String userId) {
        try {
            // StringRedisTemplate 的 opsForHash() 返回 Map<Object,Object>，需手动转换 key 类型
            Map<Object, Object> entries = redis.opsForHash().entries(KEY_PREFIX + userId);
            if (entries.isEmpty()) {
                return null;
            }
            Map<String, Object> result = new java.util.HashMap<>();
            entries.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        } catch (Exception e) {
            log.warn("读取用户 Token 统计失败: {}", e.getMessage());
            return null;
        }
    }

    // ==================== Advisor 元信息 ====================

    @Override
    public int getOrder() {
        // 数字小先执行；0 = 最外层，能统计到完整链路
        return 0;
    }

    @Override
    public String getName() {
        return "token_advisor";
    }
}
