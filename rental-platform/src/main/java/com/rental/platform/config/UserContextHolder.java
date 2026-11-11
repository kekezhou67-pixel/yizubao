package com.rental.platform.config;

import com.rental.platform.exception.BusinessException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户上下文持有者 —— 从 JwtInterceptor 注入的 request attribute 读取当前登录用户信息
 */
public class UserContextHolder {
    private record Principal(Long id, String role) {}
    private static final ThreadLocal<Principal> TOOL_PRINCIPAL = new ThreadLocal<>();
    /** Carries the authenticated request identity across streaming tool worker threads. */
    public static <T> T withToolIdentity(Long id, String role, java.util.function.Supplier<T> action) {
        Principal previous = TOOL_PRINCIPAL.get();
        TOOL_PRINCIPAL.set(new Principal(id, role));
        try { return action.get(); }
        finally { if (previous == null) TOOL_PRINCIPAL.remove(); else TOOL_PRINCIPAL.set(previous); }
    }

    private UserContextHolder() {
        // 工具类禁止实例化
    }

    /**
     * 获取当前请求的用户ID（必须已登录，否则抛出 401）
     */
    public static Long getCurrentUserId() {
        Principal scoped = TOOL_PRINCIPAL.get();
        if (scoped != null) {
            if (scoped.id() == null) throw BusinessException.unauthorized("请先登录");
            return scoped.id();
        }
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes) {
                HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
                Long userId = (Long) request.getAttribute("userId");
                if (userId != null) {
                    return userId;
                }
            }
        } catch (Exception e) {
            throw BusinessException.unauthorized("未获取到当前用户，请先登录");
        }
        throw BusinessException.unauthorized("未获取到当前用户，请检查 JWT 认证");
    }

    /**
     * 获取当前用户的角色（必须已登录）
     */
    public static String getCurrentUserRole() {
        Principal scoped = TOOL_PRINCIPAL.get();
        if (scoped != null) return scoped.role() == null ? "TENANT" : scoped.role();
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes) {
                HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
                String role = (String) request.getAttribute("role");
                return role != null ? role : "TENANT";
            }
        } catch (Exception ignored) {
        }
        return "TENANT";
    }

    /**
     * 检查当前用户是否是指定角色
     */
    public static boolean hasRole(String role) {
        return role.equalsIgnoreCase(getCurrentUserRole());
    }
}
