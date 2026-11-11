package com.rental.platform.service.impl;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.rental.platform.entity.User;
import com.rental.platform.mapper.UserMapper;
import com.rental.platform.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    private static final int BCRYPT_COST = 12;

    // ✅ 构造器注入
    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    // 注意：不能标 readOnly = true —— 下面的「明文密码自动升级」会写库，
    // 只读事务会导致 Connection is read-only（HikariCP 5.x 会强制执行）
    @Transactional
    public User login(String account, String password) {
        User user = userMapper.findByAccount(account);
        if (user == null) return null;

        String stored = user.getPassword();
        if (stored == null) return null;

        if (stored.startsWith("$2")) {
            return BCrypt.verifyer().verify(password.toCharArray(), stored).verified ? user : null;
        }

        // 旧明文密码 → 自动升级为 BCrypt
        if (password.equals(stored)) {
            String hashed = BCrypt.withDefaults().hashToString(BCRYPT_COST, password.toCharArray());
            userMapper.updatePassword(user.getId(), hashed);
            return user;
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userMapper.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public User findByAccount(String account) {
        return userMapper.findByAccount(account);
    }

    @Override
    @Transactional
    public boolean register(User user) {
        User exist = userMapper.findByAccount(user.getUsername());
        if (exist != null) return false;

        String hashed = BCrypt.withDefaults().hashToString(BCRYPT_COST, user.getPassword().toCharArray());
        user.setPassword(hashed);
        // 保留注册时指定的角色，没有指定则默认 USER
        if (user.getRole() == null || user.getRole().isEmpty()) {
            user.setRole("USER");
        }
        user.setPoints(0);
        user.setLevel("青铜");
        user.setStatus(1);
        return userMapper.insert(user) > 0;
    }

    @Override
    @Transactional
    public void addPoints(Long userId, int points, String reason, Long relatedId) {
        userMapper.addPoints(userId, points);
    }

    @Override
    @Transactional
    public boolean bindPhone(Long userId, String phone) {
        User exist = userMapper.findByAccount(phone);
        if (exist != null && !exist.getId().equals(userId)) {
            return false;
        }
        return userMapper.updatePhone(userId, phone) > 0;
    }

    @Override
    @Transactional
    public boolean bindEmail(Long userId, String email) {
        User exist = userMapper.findByAccount(email);
        if (exist != null && !exist.getId().equals(userId)) {
            return false;
        }
        return userMapper.updateEmail(userId, email) > 0;
    }

    @Override
    @Transactional
    public boolean updateAvatar(Long userId, String avatar) {
        return userMapper.updateAvatar(userId, avatar) > 0;
    }
}
