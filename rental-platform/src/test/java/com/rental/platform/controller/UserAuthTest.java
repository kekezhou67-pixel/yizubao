package com.rental.platform.controller;

import com.rental.platform.entity.User;
import com.rental.platform.service.*;
import com.rental.platform.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserAuthTest {
    private final UserService users = mock(UserService.class);
    private final VerificationCodeService codes = mock(VerificationCodeService.class);
    private final ImageCaptchaService captcha = mock(ImageCaptchaService.class);
    private final UserController controller = new UserController(users, codes, mock(JwtUtil.class), captcha);
    @Test void unknownAccountGetsRegistrationHint() {
        assertEquals("账号尚未注册，请先注册", controller.login(Map.of("account", "unknown", "password", "test-password")).getMessage());
        verify(users, never()).login(anyString(), anyString());
    }
    @Test void existingAccountGetsWrongPasswordHint() {
        when(users.findByAccount("existing")).thenReturn(new User());
        assertEquals("密码错误，请重新输入", controller.login(Map.of("account", "existing", "password", "wrong")).getMessage());
    }
    @Test void smsCannotBeSentWithoutImagePermit() {
        assertEquals("请先完成图形验证", controller.sendCode(Map.of("target", "13800000000")).getMessage());
        verifyNoInteractions(codes);
    }
    @Test void registrationCannotGrantAdminRoleOrBindUnverifiedPhone() {
        assertNotEquals(200, controller.register(Map.of("username", "test-user", "password", "test-password", "role", "ADMIN")).getCode());
        assertNotEquals(200, controller.register(Map.of("username", "test-user", "password", "test-password", "phone", "13800000000")).getCode());
        verify(users, never()).register(any());
    }
}
