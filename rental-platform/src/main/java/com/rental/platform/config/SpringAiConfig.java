package com.rental.platform.config;

import com.rental.platform.ai.advisor.TokenAdvisor;
import com.rental.platform.ai.tool.RentalTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置（AI 改造 · 阶段1）
 *
 * 依据：讲义02《Spring AI 入门》+ 讲义03 §6.2 选型结论 ——
 * 主力 AI 框架用 Spring AI（自动配置、Advisor、MCP 集成与 Spring Boot 技术栈天然契合），
 * LangChain4j 将在阶段3 @Tool 迁移验证通过后完全移除。
 *
 * ChatClient.Builder 由 spring-ai-starter-model-openai 自动配置提供，
 * 模型参数来自 application.properties 的 spring.ai.openai.*（DeepSeek 走 OpenAI 兼容协议）。
 *
 * 后续阶段将在此追加：
 * - 阶段2：memoryChatClient（挂 MessageChatMemoryAdvisor + RedisChatMemory）
 * - 阶段3：agentChatClient（defaultTools 注册 @Tool 工具）
 */
@Configuration
public class SpringAiConfig {

    /**
     * 基础 ChatClient —— 通用问答
     * 阶段1 仅用于 /api/ai/health 健康检查，验证 Spring AI 装配与 DeepSeek 连通
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        你叫小易，是易租宝租房平台的 AI 助手，服务租客与房东。
                        你擅长的领域：找房推荐、租房合同咨询、租房防坑、费用计算。
                        始终用中文回答，简洁清晰，像可靠的朋友一样交流。
                        """)
                .build();
    }

    /**
     * 带记忆 + Token 统计的 ChatClient（阶段2）
     * - MessageChatMemoryAdvisor：自动把 RedisChatMemory 里的历史拼进请求、把新消息写回
     *   ⚠️ 调用侧必须传 advisor 参数 ChatMemory.CONVERSATION_ID（= "chat_memory_conversation_id"），
     *      参数名写错会静默失效（讲义02 的坑），统一用常量避免手拼
     * - TokenAdvisor：统计每次调用的 input/output tokens
     */
    @Bean
    public ChatClient memoryChatClient(ChatClient.Builder builder,
                                       ChatMemory chatMemory,
                                       TokenAdvisor tokenAdvisor) {
        return builder
                .defaultSystem("""
                        你叫小易，是易租宝租房平台的 AI 助手，服务租客与房东。
                        你擅长的领域：找房推荐、租房合同咨询、租房防坑、费用计算。
                        始终用中文回答，简洁清晰，像可靠的朋友一样交流。
                        """)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        tokenAdvisor)
                .build();
    }

    /**
     * Agent ChatClient（阶段3）—— 挂载 @Tool 工具集
     *
     * Agent Loop 由 Spring AI 自动处理（讲义03 第2章）：
     * 模型根据工具 description 自主决定调哪个、调几次、按什么顺序，
     * 直到不再返回 tool_call 直接给文本 —— 无需手写意图解析和 switch 分发。
     *
     * 也挂记忆 Advisor：conversationId 按用户固定（agent-{userId}），
     * 修复技术债#3"对话历史由前端传入、刷新丢失"。
     */
    @Bean
    public ChatClient agentChatClient(ChatClient.Builder builder,
                                      RentalTools rentalTools,
                                      ChatMemory chatMemory,
                                      TokenAdvisor tokenAdvisor) {
        return builder
                .defaultSystem(AGENT_BEHAVIOR_RULES)
                .defaultTools(rentalTools)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        tokenAdvisor)
                .build();
    }

    /**
     * Agent 行为准则 —— Controller 调用时会拼在四种模式人设之后一起作为 system
     */
    public static final String AGENT_BEHAVIOR_RULES = """
            你是智能租房平台「易租宝」的 AI 管家，名字叫「小易」。你不仅能聊天，还能通过工具真正执行操作。

            私信规则：联系房东、站内私信、约看房时，使用 preparePrivateMessage 准备草稿。页面会展示收件人和全文，用户必须亲自点击确认按钮才发送；你没有发送私信工具。即便用户在对话中说确认，也应请他点卡片按钮。不要说已经发出，不要改用短信代替私信。
            行为准则：
            1. 涉及房源、地铁、攻略、费用等数据的问题，必须调用工具查询真实数据，禁止编造房源和价格
            2. 写操作（发布房源、发短信）必须两步走：先调预览工具 → 向用户完整复述内容并询问确认 → 用户明确同意后才调 confirm 开头的工具
            3. 收藏房源前，先调 getHouseDetail 确认房源存在
            4. 用户说"第N个"指的是最近一次 searchHouses 返回的 houses 列表中 rank=N 的房源，操作时使用该房源的 id 字段（注意：rank 是列表序号，不是房源 id，两者不同！）
            5. 向用户回复房源搜索结果时，每套房源必须标注它的 id（如 "ID:1005"），这样用户说"收藏第N个"时才能准确定位
            6. 如果对话历史里找不到房源 id，就重新调用 searchHouses 按之前的条件搜索，从新结果里取 rank=N 的 id 来操作，不要反问用户要 id，也不要拿 N 当 id 用
            7. 闲聊、咨询建议类问题直接回答，不必调用工具
            8. 回复引用数据时要具体（价格、面积、位置、数量），并给出下一步建议
            """;
}
