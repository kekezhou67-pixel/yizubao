package com.rental.platform.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * MCP 端点鉴权过滤器（AI 改造 · 阶段8，讲义03 §7 生产级要求）
 *
 * MCP 端点（/mcp、/mcp/message）不经过 JWT 用户体系（调用方是外部 AI 应用），
 * 用独立 Bearer Token 鉴权：Authorization: Bearer {mcp.auth.token}
 * 无 token / token 错误 → 401，防止平台能力被任意匿名调用。
 */
@Component
public class McpAuthFilter extends OncePerRequestFilter {

    private final String expectedToken;

    public McpAuthFilter(@Value("${mcp.auth.token:rental-mcp-dev-token}") String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        // 只拦 MCP 端点，其他请求直接放行
        if (uri == null || !uri.startsWith("/mcp")) {
            filterChain.doFilter(request, response);
            return;
        }

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.equals("Bearer " + expectedToken)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"error\":\"unauthorized\",\"message\":\"MCP 访问需要 Bearer Token\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
