package com.rental.platform.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server 配置（AI 改造 · 阶段8，讲义03 §7）
 *
 * 传输：SSE 双端点（GET /mcp 建长连接推结果，POST /mcp/message 发 JSON-RPC 报文）
 * 注册：MethodToolCallbackProvider 扫描 McpTools 的 @Tool 方法 → tools/list 可发现
 *
 * 配置见 application.properties 的 spring.ai.mcp.server.*
 * 鉴权见 McpAuthFilter（Bearer Token，无 token 401）
 */
@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider rentalMcpTools(McpTools mcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(mcpTools)
                .build();
    }
}
