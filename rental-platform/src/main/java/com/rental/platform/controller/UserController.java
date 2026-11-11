package com.rental.platform.controller;

import com.rental.platform.config.UserContextHolder;
import com.rental.platform.entity.User;
import com.rental.platform.service.UserService;
import com.rental.platform.service.ImageCaptchaService;
import com.rental.platform.service.VerificationCodeService;
import com.rental.platform.utils.JwtUtil;
import com.rental.platform.utils.Result;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户 Controller —— 构造器注入 + 统一用 UserContextHolder 获取当前用户
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final VerificationCodeService codeService;
    private final JwtUtil jwtUtil;
    private final ImageCaptchaService captcha;

    // ✅ 构造器注入
    public UserController(UserService userService, VerificationCodeService codeService, JwtUtil jwtUtil, ImageCaptchaService captcha) {
        this.userService = userService;
        this.codeService = codeService;
        this.jwtUtil = jwtUtil;
        this.captcha = captcha;
    }

    /**
     * 密码登录（支持用户名/邮箱/手机号）
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> params) {
        String account = params.get("account");
        String password = params.get("password");

        if (account == null || account.trim().isEmpty()) {
            return Result.error("请输入用户名/邮箱/手机号");
        }
        if (password == null || password.trim().isEmpty()) {
            return Result.error("请输入密码");
        }

        if (userService.findByAccount(account.trim()) == null) return Result.error("账号尚未注册，请先注册");
        User user = userService.login(account.trim(), password);
        if (user == null) {
            return Result.error("密码错误，请重新输入");
        }

        if (!Integer.valueOf(1).equals(user.getStatus())) return Result.error("账号已停用，请联系平台处理");
        return Result.success("登录成功", buildLoginData(user));
    }

    @GetMapping("/captcha")
    public Result<Map<String, String>> imageCaptcha() {
        return Result.success(captcha.create());
    }

    @PostMapping("/captcha/verify")
    public Result<Map<String, String>> verifyCaptcha(@RequestBody Map<String, String> params) {
        String phone = params.getOrDefault("target", "").trim();
        if (!phone.matches("^1[3-9]\\d{9}$")) return Result.error("请输入正确的11位手机号");
        String ticket = captcha.verify(params.get("id"), params.get("answer"), phone);
        return ticket == null ? Result.error("图形验证码错误或已过期，请换一张重试") : Result.success(Map.of("ticket", ticket));
    }

    /**
     * 发送验证码（手机号/邮箱）
     */
    @PostMapping("/send-code")
    public Result<String> sendCode(@RequestBody Map<String, String> params) {
        String target = params.get("target");
        if (target == null || target.trim().isEmpty()) {
            return Result.error("请输入手机号或邮箱");
        }
        if (!target.trim().matches("^1[3-9]\\d{9}$")) return Result.error("请输入正确的11位手机号");
        if (!captcha.consume(params.get("captchaTicket"), target.trim())) return Result.error("请先完成图形验证");
        codeService.generateCode(target.trim());
        return Result.success("验证码已发送，5分钟内有效", null);
    }

    /**
     * 验证码登录
     */
    @PostMapping("/login-by-code")
    public Result<Map<String, Object>> loginByCode(@RequestBody Map<String, String> params) {
        String target = params.get("target");
        String code = params.get("code");

        if (target == null || target.trim().isEmpty()) {
            return Result.error("请输入手机号或邮箱");
        }
        if (code == null || code.trim().isEmpty()) {
            return Result.error("请输入验证码");
        }
        if (!codeService.verify(target.trim(), code.trim())) {
            return Result.error("验证码错误或已过期");
        }

        User user = userService.findByAccount(target.trim());
        if (user == null) {
            return Result.error("账号尚未注册，请先注册");
        }

        if (!Integer.valueOf(1).equals(user.getStatus())) return Result.error("账号已停用，请联系平台处理");
        // 智能绑定：手机号验证码登录且该用户未绑定手机号时，自动绑定
        String targetTrim = target.trim();
        if (!targetTrim.contains("@") && targetTrim.matches("^1[3-9]\\d{9}$")
                && (user.getPhone() == null || user.getPhone().isEmpty())) {
            userService.bindPhone(user.getId(), targetTrim);
            user.setPhone(targetTrim);
        }

        Map<String, Object> data = buildLoginData(user);
        data.put("phone", user.getPhone());
        data.put("email", user.getEmail());
        return Result.success("登录成功", data);
    }

    /**
     * 注册
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");
        String email = params.get("email");
        String phone = params.get("phone");
        String role = params.getOrDefault("role", "TENANT");

        if (username == null || username.trim().isEmpty()) {
            return Result.error("用户名不能为空");
        }
        if (password == null || password.trim().isEmpty()) {
            return Result.error("密码不能为空");
        }

        if (username.trim().length() < 2 || username.trim().length() > 20) return Result.error("用户名需为2—20个字符");
        if (password.length() < 6 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) return Result.error("密码至少6位，且不能超过72字节");
        if (!"TENANT".equals(role) && !"LANDLORD".equals(role)) return Result.error("请选择租客或房东身份");
        if (phone != null && !phone.isBlank() && !phone.matches("^1[3-9]\\d{9}$")) return Result.error("手机号格式不正确");
        if (email != null && !email.isBlank() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) return Result.error("邮箱格式不正确");
        if (userService.findByAccount(username.trim()) != null) return Result.error("用户名已注册，请更换用户名或直接登录");
        if (phone != null && !phone.isBlank() && userService.findByAccount(phone.trim()) != null) return Result.error("该手机号已注册，请直接登录");
        if (phone != null && !phone.isBlank() && !codeService.verify(phone.trim(), params.getOrDefault("code", "").trim())) return Result.error("手机验证码错误或已过期");
        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(password);
        user.setEmail(email);
        user.setPhone(phone);
        user.setRole(role);

        boolean success = userService.register(user);
        return success ? Result.success("注册成功", null) : Result.error("用户名已存在");
    }

    /** 查询用户公开信息 */
    @GetMapping("/info/{id}")
    public Result<User> getUserInfo(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) return Result.error("用户不存在");
        user.setPassword(null);
        return Result.success(user);
    }

    /** 检查用户是否存在 */
    @PostMapping("/check")
    public Result<Map<String, Object>> checkUser(@RequestBody Map<String, String> params) {
        String target = params.get("target");
        if (target == null || target.trim().isEmpty()) return Result.error("请输入手机号或邮箱");
        User user = userService.findByAccount(target.trim());
        Map<String, Object> data = new HashMap<>();
        data.put("exists", user != null);
        if (user != null) { data.put("username", user.getUsername()); data.put("role", user.getRole()); }
        return Result.success(data);
    }

    /** 获取当前登录用户信息 */
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        Long userId = UserContextHolder.getCurrentUserId();
        User user = userService.findById(userId);
        if (user == null) return Result.error("用户不存在");
        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());
        data.put("role", user.getRole());
        data.put("points", user.getPoints());
        data.put("level", user.getLevel());
        data.put("avatar", user.getAvatar());
        data.put("realName", user.getRealName());
        return Result.success(data);
    }

    /** 绑定手机号 */
    @PostMapping("/bind-phone")
    public Result<String> bindPhone(@RequestBody Map<String, String> params) {
        Long userId = UserContextHolder.getCurrentUserId();
        String phone = params.get("phone");
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) return Result.error("请输入正确的11位手机号");
        if (!codeService.verify(phone.trim(), params.getOrDefault("code", "").trim())) return Result.error("手机验证码错误或已过期");
        boolean ok = userService.bindPhone(userId, phone.trim());
        return ok ? Result.success("手机号绑定成功", null) : Result.error("该手机号已被其他账号绑定");
    }

    /** 绑定邮箱 */
    @PostMapping("/bind-email")
    public Result<String> bindEmail(@RequestBody Map<String, String> params) {
        Long userId = UserContextHolder.getCurrentUserId();
        String email = params.get("email");
        if (email == null || !email.matches("^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$")) return Result.error("邮箱格式不正确");
        boolean ok = userService.bindEmail(userId, email.trim());
        return ok ? Result.success("邮箱绑定成功", null) : Result.error("该邮箱已被其他账号绑定");
    }

    /** 更新用户资料 */
    @PostMapping("/update-profile")
    public Result<String> updateProfile(@RequestBody Map<String, String> params) {
        Long userId = UserContextHolder.getCurrentUserId();
        String username = params.get("username");
        String avatar = params.get("avatar");

        int updated = 0;
        if (username != null && !username.trim().isEmpty()) {
            // 更新用户名需要检查重复
            User exist = userService.findByAccount(username.trim());
            if (exist != null && !exist.getId().equals(userId)) {
                return Result.error("该用户名已被占用");
            }
            // 简化处理：这里暂用 avatar 字段传递，实际需扩展 UserService
        }
        if (avatar != null && !avatar.trim().isEmpty()) {
            userService.updateAvatar(userId, avatar.trim());
            updated++;
        }
        return updated > 0 ? Result.success("更新成功", null) : Result.error("没有需要更新的内容");
    }

    // ===== 私有辅助 =====

    private Map<String, Object> buildLoginData(User user) {
        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("role", user.getRole());
        data.put("points", user.getPoints());
        data.put("level", user.getLevel());
        data.put("avatar", user.getAvatar());
        return data;
    }
}
