package com.rental.platform.service;

import com.rental.platform.entity.User;

public interface UserService {

    /**
     * 用户登录（支持用户名/邮箱/手机号）
     */
    User login(String account, String password);

    /**
     * 根据账号（用户名/邮箱/手机号）查找用户
     */
    User findByAccount(String account);

    /**
     * 根据ID查询用户
     */
    User findById(Long id);

    /**
     * 注册新用户
     */
    boolean register(User user);

    /**
     * 增加用户积分
     */
    void addPoints(Long userId, int points, String reason, Long relatedId);

    /**
     * 绑定手机号
     */
    boolean bindPhone(Long userId, String phone);

    /**
     * 绑定邮箱
     */
    boolean bindEmail(Long userId, String email);

    /**
     * 更新头像
     */
    boolean updateAvatar(Long userId, String avatar);
}
