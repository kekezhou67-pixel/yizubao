package com.rental.platform.controller;

import com.rental.platform.ai.advisor.TokenAdvisor;
import com.rental.platform.config.SpringAiConfig;
import com.rental.platform.config.UserContextHolder;
import com.rental.platform.utils.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * AI 专区接口（AI 改造 · 阶段3 完成态 —— LangChain4j 已完全移除，全量 Spring AI）
 *
 * 接口清单：
 * - POST /api/ai/chat          多轮对话（历史由前端传入，行为与旧版一致）
 * - POST /api/ai/chat/stream   流式对话（SSE + Redis 记忆，阶段2 新增）
 * - POST /api/ai/agent         Agent 对话（@Tool 工具调用，前端契约 reply/mode/actions 不变）
 * - GET  /api/ai/health        健康检查（阶段1 新增）
 * - GET  /api/ai/token/stats   Token 用量统计（阶段2 新增）
 * - DELETE /api/ai/chat/memory/{conversationId}  清空会话记忆（阶段2 新增）
 *
 * 前端契约（AIChat.vue 调用 /agent）：
 *   请求 {mode, messages[]} → 响应 {reply, mode, actions[]}
 *   actions 字段必须保留（新链路下为空数组 —— Spring AI Agent Loop 自动执行工具）
 */
@RestController
@RequestMapping("/api/ai")
public class AIChatController {

    private static final Logger log = LoggerFactory.getLogger(AIChatController.class);

    @org.springframework.beans.factory.annotation.Autowired
    private com.rental.platform.service.PrivateMessageService privateMessages;
    private final ChatClient chatClient;
    private final ChatClient memoryChatClient;
    private final ChatClient agentChatClient;
    private final TokenAdvisor tokenAdvisor;
    private final ChatMemory chatMemory;

    // ✅ 构造器注入（AI bean 可选，没配置 AI 时也能启动）
    // 注意：ChatClient 有三个 Bean（chatClient / memoryChatClient / agentChatClient），按参数名匹配 Bean 名
    public AIChatController(
            @org.springframework.beans.factory.annotation.Autowired(required = false) ChatClient chatClient,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ChatClient memoryChatClient,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ChatClient agentChatClient,
            @org.springframework.beans.factory.annotation.Autowired(required = false) TokenAdvisor tokenAdvisor,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.memoryChatClient = memoryChatClient;
        this.agentChatClient = agentChatClient;
        this.tokenAdvisor = tokenAdvisor;
        this.chatMemory = chatMemory;
    }

    // ==================== 四种租房模式 System Instruction ====================

    private static final String HOUSE_FIND_SYSTEM_INSTRUCTION =
            "你是一个专业的租房顾问，帮用户找到合适的房子。你熟悉全国主要城市的租房市场，了解各区域的租金水平和生活配套。\n\n" +
            "你的能力：\n" +
            "1. 根据用户预算、位置偏好、户型需求推荐合适的城市区域\n" +
            "2. 分析不同区域的租金行情和性价比\n" +
            "3. 介绍地铁沿线的租房热点\n" +
            "4. 提醒用户注意通勤时间、周边配套等实际因素\n\n" +
            "回复要求：\n" +
            "- 先理解用户需求（预算、城市、上班地点、房型偏好等）\n" +
            "- 信息不足时主动追问\n" +
            "- 给出具体的小区/区域名称建议，不要泛泛而谈\n" +
            "- 用实际租金范围说话，不要只说「看情况」\n" +
            "- 最后留一个可操作的下一步建议\n\n" +
            "始终用中文，像朋友一样聊天，不要像机器人。";

    private static final String CONTRACT_SYSTEM_INSTRUCTION =
            "你是一个租房法律顾问，专注于帮助租客理解租房合同、维护合法权益。你熟悉《民法典》租赁合同章节和各地租房相关法规。\n\n" +
            "你的能力：\n" +
            "1. 解释租房合同中的常见条款和法律术语\n" +
            "2. 指出合同中的风险条款和不合理要求\n" +
            "3. 提供押金退还、提前解约、涨租等方面的法律建议\n" +
            "4. 告知租客在遇到纠纷时的维权途径\n\n" +
            "回复要求：\n" +
            "- 用通俗易懂的语言解释法律概念\n" +
            "- 给出具体的合同条款示例（怎么约定才算公平）\n" +
            "- 区分「法律明确规定」和「建议协商」的事项\n" +
            "- 必要时提醒用户保留证据（聊天记录、转账凭证、照片等）\n" +
            "- 涉及重大纠纷时建议咨询专业律师\n\n" +
            "始终用中文，专业但不冷冰冰，像热心的朋友在帮你出主意。";

    private static final String PITFALL_SYSTEM_INSTRUCTION =
            "你是一个租房防坑专家，见过无数租房骗局和陷阱。你的使命是帮租客避开这些坑。\n\n" +
            "你的知识领域：\n" +
            "1. 虚假房源识别：图片盗用、价格异常低、钓鱼贴\n" +
            "2. 看房技巧：检查水电、隔音、采光、潮湿、虫害等\n" +
            "3. 二房东风险：转租合法性、押金风险、水电费纠纷\n" +
            "4. 中介套路：假房源引流、哄抬价格、隐瞒信息\n" +
            "5. 收费陷阱：看房费、定金不退、各类名目的杂费\n\n" +
            "回复要求：\n" +
            "- 给用户一个可操作的检查清单\n" +
            "- 用真实案例说明问题（\"有租客就是这样被坑的\"）\n" +
            "- 每个建议都要告诉用户「怎么做」+\"为什么\"\n" +
            "- 语气既警惕又不制造焦虑\n\n" +
            "始终用中文，像一个有经验的过来人在分享干货。";

    private static final String COST_SYSTEM_INSTRUCTION =
            "你是一个租房成本分析专家，帮用户算清楚租房到底要花多少钱。\n\n" +
            "你能计算和分析：\n" +
            "1. 租房初始费用：押金（通常1-2月）、首月租金、中介费（半月到一月租金）\n" +
            "2. 每月固定支出：租金、水电燃气、网费、物业费\n" +
            "3. 不同区域/户型/城市的租金对比\n" +
            "4. 租房 vs 买房的长期成本分析\n" +
            "5. 一个房源的综合性价比评估\n\n" +
            "回复要求：\n" +
            "- 列出明细，不要只说总数\n" +
            "- 给出价格区间而不是单一数字\n" +
            "- 区分「必须花的」和「可能花的」\n" +
            "- 如果信息不足，主动询问城市、户型、区域等关键参数\n" +
            "- 最后总结一句建议\n\n" +
            "始终用中文，像财务顾问一样精准但不刻板。";

    // ==================== 健康检查（阶段1） ====================

    /**
     * AI 健康检查 —— 验证 Spring AI ChatClient 装配成功且 DeepSeek 可连通
     * GET /api/ai/health
     */
    @GetMapping("/health")
    public Result<String> health() {
        if (chatClient == null) {
            return Result.error("AI 未配置：ChatClient Bean 缺失");
        }
        try {
            String reply = chatClient.prompt()
                    .user("用一句话介绍你自己")
                    .call()
                    .content();
            return Result.success(reply);
        } catch (Exception e) {
            log.error("AI 健康检查失败: {}", e.getMessage());
            return Result.error("AI 调用失败: " + e.getMessage());
        }
    }

    // ==================== 普通对话（前端契约不变，内部已换 Spring AI） ====================

    /**
     * AI 专区多轮对话
     * POST /api/ai/chat
     * 请求体: { "mode": "house_find|contract|pitfall|cost", "messages": [...] }
     * 历史消息由前端传入（与旧版行为一致）
     */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> params) {
        String mode = (String) params.getOrDefault("mode", "house_find");
        Object messagesObj = params.get("messages");

        String systemInstruction = getSystemInstructionForMode(mode);

        // 解析历史消息 → Spring AI Message 类型
        List<Message> history = new ArrayList<>();
        String latestUserMessage = "";
        if (messagesObj instanceof List) {
            List<Map<String, String>> messages = (List<Map<String, String>>) messagesObj;
            for (int i = 0; i < messages.size(); i++) {
                Map<String, String> msg = messages.get(i);
                String role = msg.get("role");
                String content = msg.get("content");
                if (content == null) continue;

                if (i == messages.size() - 1 && "user".equals(role)) {
                    latestUserMessage = content;
                } else if ("user".equals(role)) {
                    history.add(new UserMessage(content));
                } else if ("assistant".equals(role)) {
                    history.add(new AssistantMessage(content));
                }
                // system 消息跳过（由后端根据 mode 统一设置）
            }
        }

        if (latestUserMessage.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("reply", getWelcomeMessage(mode));
            result.put("mode", mode);
            return Result.success(result);
        }

        if (chatClient == null) {
            return Result.error("AI 服务未配置，请设置 DEEPSEEK_API_KEY 环境变量后重启");
        }
        try {
            String aiReply = chatClient.prompt()
                    .system(systemInstruction)
                    .messages(history)
                    .user(latestUserMessage)
                    .call()
                    .content();

            Map<String, Object> result = new HashMap<>();
            result.put("reply", aiReply);
            result.put("mode", mode);
            return Result.success(result);
        } catch (Exception e) {
            log.error("AI Chat 失败, mode={}: {}", mode, e.getMessage());
            return Result.error("AI 回复失败：" + e.getMessage());
        }
    }

    // ==================== 流式对话 + Token 统计（阶段2） ====================

    /**
     * 流式对话（SSE）—— 逐字输出 + Redis 对话记忆
     * POST /api/ai/chat/stream
     * 请求体: { "message": "...", "conversationId": "...", "mode": "house_find|contract|pitfall|cost", "userId": 123 }
     *
     * ⚠️ conversationId 相同的请求共享记忆（Redis List，保留最近40条，TTL 7天）
     * ⚠️ advisor 参数名用 ChatMemory.CONVERSATION_ID 常量（= "chat_memory_conversation_id"），
     *    手拼写错会静默失效 —— 讲义02 的坑
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody Map<String, Object> params) {
        String message = params.get("message") != null ? params.get("message").toString() : "";
        String conversationId = params.get("conversationId") != null
                ? params.get("conversationId").toString() : ChatMemory.DEFAULT_CONVERSATION_ID;
        String mode = params.get("mode") != null ? params.get("mode").toString() : "house_find";
        String userId = params.get("userId") != null ? params.get("userId").toString() : null;

        if (message.isBlank()) {
            return Flux.just("请输入您的问题");
        }
        if (memoryChatClient == null) {
            return Flux.just("AI 服务未配置，请设置 DEEPSEEK_API_KEY 环境变量后重启");
        }
        return memoryChatClient.prompt()
                .system(getSystemInstructionForMode(mode))
                .user(message)
                .advisors(a -> {
                    a.param(ChatMemory.CONVERSATION_ID, conversationId);
                    if (userId != null) {
                        a.param(TokenAdvisor.USER_ID_PARAM, userId);
                    }
                })
                .stream()
                .content();
    }

    /**
     * Token 用量统计
     * GET /api/ai/token/stats          —— 全局（进程内累计，重启清零）
     * GET /api/ai/token/stats?userId=1 —— 附带该用户的 Redis 持久累计
     */
    @GetMapping("/token/stats")
    public Result<Map<String, Object>> tokenStats(@RequestParam(required = false) String userId) {
        if (tokenAdvisor == null) {
            return Result.error("Token 统计未启用");
        }
        Map<String, Object> stats = new HashMap<>();
        stats.put("inputTokens", tokenAdvisor.getInputTokens());
        stats.put("outputTokens", tokenAdvisor.getOutputTokens());
        stats.put("totalTokens", tokenAdvisor.getInputTokens() + tokenAdvisor.getOutputTokens());
        stats.put("calls", tokenAdvisor.getCallCount());
        if (userId != null && !userId.isBlank()) {
            Map<String, Object> userStats = tokenAdvisor.getUserStats(userId);
            stats.put("user", userStats != null ? userStats : Map.of("tip", "该用户暂无用量记录"));
        }
        return Result.success(stats);
    }

    /**
     * 清空某个会话的记忆
     * DELETE /api/ai/chat/memory/{conversationId}
     */
    @DeleteMapping("/chat/memory/{conversationId}")
    public Result<String> clearMemory(@PathVariable String conversationId) {
        if (chatMemory == null) {
            return Result.error("记忆组件未启用");
        }
        chatMemory.clear(conversationId);
        return Result.success("会话记忆已清空: " + conversationId);
    }

    // ==================== Agent 模式（阶段3：Spring AI @Tool 全量接管） ====================

    /**
     * AI Agent 对话 —— AI 通过 @Tool 真正执行操作（搜房/收藏/查地铁/算费用/审合同/生成文档等）
     * POST /api/ai/agent
     * 请求体: { "mode": "house_find|contract|pitfall|cost", "messages": [...] }
     * 响应:   { "reply": "AI回复", "mode": "...", "actions": [] }   ← 前端契约不变
     *
     * 实现：agentChatClient 挂载 RentalTools 的 15 个 @Tool，
     * Agent Loop 由 Spring AI 自动编排（模型自主决定调哪个工具、调几次），
     * 替代旧版"手写 JSON 意图解析 + 13 分支 switch 分发"（技术债#1#2 已清除）。
     *
     * 记忆：conversationId = "agent-{userId}"，同一用户跨请求共享（修复技术债#3 刷新丢失）。
     * 容错：网络抖动重试 3 次（0.8s/1.6s 退避）—— 实测本地代理环境偶发 PrematureCloseException。
     */
    @PostMapping("/agent")
    public Result<Map<String, Object>> agent(@RequestBody Map<String, Object> params) {
        String mode = (String) params.getOrDefault("mode", "house_find");
        Object messagesObj = params.get("messages");

        // 提取最新一条用户消息（历史上下文由服务端 Redis 记忆接管）
        String latestUserMessage = "";
        if (messagesObj instanceof List) {
            List<Map<String, String>> messages = (List<Map<String, String>>) messagesObj;
            for (int i = messages.size() - 1; i >= 0; i--) {
                Map<String, String> msg = messages.get(i);
                if ("user".equals(msg.get("role")) && msg.get("content") != null) {
                    latestUserMessage = msg.get("content");
                    break;
                }
            }
        }

        if (latestUserMessage.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("reply", getWelcomeMessage(mode));
            result.put("mode", mode);
            result.put("actions", List.of());
            return Result.success(result);
        }

        if (agentChatClient == null) {
            return Result.error("AI 服务未配置，请设置 DEEPSEEK_API_KEY 环境变量后重启");
        }

        Long userId = null;
        try {
            userId = UserContextHolder.getCurrentUserId();
        } catch (Exception ignored) { /* 游客 */ }
        final Long uid = userId;
        final String conversationId = "agent-" + (userId != null ? userId : "guest");

        final java.util.Set<Object> existingDraftIds = uid == null ? java.util.Set.of() : privateMessages.pending(uid).stream().map(d -> d.get("id")).collect(java.util.stream.Collectors.toSet());
        Exception lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String reply = agentChatClient.prompt()
                        // 四种模式人设 + Agent 行为准则，一起作为 system
                        .system(getSystemInstructionForMode(mode) + "\n\n" + SpringAiConfig.AGENT_BEHAVIOR_RULES)
                        .user(latestUserMessage)
                        .advisors(a -> {
                            a.param(ChatMemory.CONVERSATION_ID, conversationId);
                            if (uid != null) {
                                a.param(TokenAdvisor.USER_ID_PARAM, String.valueOf(uid));
                            }
                        })
                        .call()
                        .content();

                // ★ 前端契约保持不变：reply / mode / actions 三个字段
                //   新链路由 Spring AI 自动执行工具，actions 保留空数组（字段不能删）
                Map<String, Object> result = new HashMap<>();
                result.put("reply", reply);
                result.put("mode", mode);
                result.put("actions", List.of());
                result.put("privateDrafts",uid == null ? List.of() : privateMessages.pending(uid).stream().filter(d -> !existingDraftIds.contains(d.get("id"))).toList());
                return Result.success(result);
            } catch (Exception e) {
                lastError = e;
                log.warn("Spring AI Agent 第{}次尝试失败: {}", attempt, e.getMessage());
                if (attempt < 3) {
                    try {
                        Thread.sleep(attempt * 800L);   // 简单退避：0.8s / 1.6s
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("Agent 三次尝试均失败, mode={}: {}", mode,
                lastError != null ? lastError.getMessage() : "unknown");
        return Result.error("AI 服务暂时不可用，请稍后重试");
    }

    // ==================== 私有辅助 ====================

    /**
     * 根据模式获取对应的 System Instruction
     */
    private String getSystemInstructionForMode(String mode) {
        switch (mode) {
            case "house_find": return "你叫小易，是易租宝的租房助手。\n" + HOUSE_FIND_SYSTEM_INSTRUCTION;
            case "contract":   return "你叫小易，是易租宝的租房助手。\n" + CONTRACT_SYSTEM_INSTRUCTION;
            case "pitfall":    return "你叫小易，是易租宝的租房助手。\n" + PITFALL_SYSTEM_INSTRUCTION;
            case "cost":       return "你叫小易，是易租宝的租房助手。\n" + COST_SYSTEM_INSTRUCTION;
            default:           return "你叫小易，是易租宝的租房助手。\n" + HOUSE_FIND_SYSTEM_INSTRUCTION;
        }
    }

    private String getWelcomeMessage(String mode) {
        switch (mode) {
            case "house_find":
                return "你好！我是**小易**，你的租房顾问，帮你找到最适合的房子。\n\n" +
                        "告诉我你的预算、上班地点和户型偏好，我来分析哪个区域最合适。\n\n" +
                        "例如：\n" +
                        "- \"我在南山科技园上班，预算4000，想租一居室\"\n" +
                        "- \"帮我对比宝安和龙华的租房性价比\"\n" +
                        "- \"推荐几个深圳地铁沿线的租房热点\"";
            case "contract":
                return "你好！我是**合同咨询专家**。\n\n" +
                        "把你看到的合同条款贴过来，或者直接说你的疑问，我帮你排查风险。\n\n" +
                        "例如：\n" +
                        "- \"合同写押金退房后30天退，合理吗？\"\n" +
                        "- \"房东每年涨租10%，法律允许吗？\"\n" +
                        "- \"提前退租要赔2个月租金，能不能协商？\"";
            case "pitfall":
                return "你好！我是**防坑助手**，帮你躲开租房里的各种套路。\n\n" +
                        "告诉我你要看房、签约还是交钱，我告诉你注意什么。\n\n" +
                        "例如：\n" +
                        "- \"明天看房要注意哪些细节？\"\n" +
                        "- \"网上有个比周边便宜好多的房子靠谱吗？\"\n" +
                        "- \"房东说不签合同直接交钱就行，能信吗？\"";
            case "cost":
                return "你好！我是**费用评估师**。\n\n" +
                        "告诉我城市和户型，我帮你把租房花费一笔一笔算清楚。\n\n" +
                        "例如：\n" +
                        "- \"深圳两居室，第一次租房要准备多少钱？\"\n" +
                        "- \"北京朝阳区租房一个月总共要花多少？\"\n" +
                        "- \"月租3000，租房5年vs买房5年哪个划算？\"";
            default:
                return "你好！欢迎使用小易租房顾问，请选择一个模式开始对话。";
        }
    }
}
