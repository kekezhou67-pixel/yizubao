package com.rental.platform.config;

import com.alibaba.fastjson.JSON;
import com.rental.platform.utils.JwtUtil;
import com.rental.platform.utils.Result;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.List;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final com.rental.platform.mapper.UserMapper users;

    // ✅ 构造器注入
    public JwtInterceptor(JwtUtil jwtUtil, com.rental.platform.mapper.UserMapper users) {
        this.jwtUtil = jwtUtil;
        this.users = users;
    }

    // 无需登录的公开接口（登录、注册、账号检查、发送验证码）
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
        "/api/user/login", "/api/user/login-by-code", "/api/user/register",
        "/api/user/captcha", "/api/user/captcha/verify", "/api/user/check", "/api/user/send-code"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // OPTIONS 预检请求放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 公开接口放行（登录、注册、账号检查、发送验证码）
        String path = request.getRequestURI();
        for (String publicPath : PUBLIC_PATHS) {
            if (path.equals(publicPath)) {
                return true;
            }
        }

        // GET 请求（读操作）游客可访问
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            // 尝试解析 token，有则注入用户信息
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    Claims claims = jwtUtil.parseToken(authHeader.substring(7));
                    var user=users.findById(Long.valueOf(claims.getSubject()));
                    if(user==null||!Integer.valueOf(1).equals(user.getStatus())) { sendUnauthorized(response,"账号已停用，请联系平台处理");return false; }
                    request.setAttribute("userId", user.getId());
                    request.setAttribute("username", user.getUsername());
                    request.setAttribute("role", user.getRole());
                } catch (Exception ignored) { /* token无效也不拦截读操作 */ }
            }
            return true;
        }

        // POST/PUT/DELETE 需要登录
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(response, "请先登录");
            return false;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtUtil.parseToken(token);
            var user=users.findById(Long.valueOf(claims.getSubject()));
            if(user==null||!Integer.valueOf(1).equals(user.getStatus())) { sendUnauthorized(response,"账号已停用，请联系平台处理");return false; }
            // 将用户信息注入 request attribute，供 UserContextHolder 读取
            request.setAttribute("userId", Long.valueOf(claims.getSubject()));
            request.setAttribute("username", claims.get("username", String.class));
            request.setAttribute("role", user.getRole());
            return true;
        } catch (ExpiredJwtException e) {
            sendUnauthorized(response, "登录已过期，请重新登录");
            return false;
        } catch (MalformedJwtException | IllegalArgumentException e) {
            sendUnauthorized(response, "Token无效");
            return false;
        }
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        // ✅ 使用 Result + JSON 序列化，替代手动拼接字符串
        response.getWriter().write(JSON.toJSONString(Result.error(401, message)));
    }
}

