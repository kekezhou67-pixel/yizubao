package com.rental.platform.controller;

import com.rental.platform.config.UserContextHolder;
import com.rental.platform.entity.User;
import com.rental.platform.exception.BusinessException;
import com.rental.platform.mapper.HouseMapper;
import com.rental.platform.mapper.UserMapper;
import com.rental.platform.utils.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 Controller —— 构造器注入 + 管理员权限校验
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final HouseMapper houseMapper;
    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;
    private final com.rental.platform.service.PlatformWorkflowService flow;

    // ✅ 构造器注入
    public AdminController(HouseMapper houseMapper, UserMapper userMapper, JdbcTemplate jdbcTemplate, com.rental.platform.service.PlatformWorkflowService flow) {
        this.flow=flow;
        this.houseMapper = houseMapper;
        this.userMapper = userMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ===== 权限校验工具方法 =====

    private void checkAdmin() {
        String role = UserContextHolder.getCurrentUserRole();
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw BusinessException.forbidden("仅管理员可访问");
        }
    }

    // ===== 用户管理 =====

    @GetMapping("/users")
    public Result<List<User>> getAllUsers() {
        checkAdmin();
        List<User> users = userMapper.findAll();
        users.forEach(user -> user.setPassword(null));
        return Result.success(users);
    }

    @PutMapping("/user/{id}/status")
    public Result<String> toggleUserStatus(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        checkAdmin();
        Integer status = params.get("status") instanceof Number n ? n.intValue() : null;
        if (status == null) {
            return Result.error("状态不能为空");
        }
        flow.userStatus(id,status,com.rental.platform.service.HousingTrustService.text(params,"note",1000));
        return Result.success("操作成功", null);
    }

    // ===== 统计面板 =====

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        checkAdmin();
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalHouses", houseMapper.countAll());
        stats.put("pendingHouses", houseMapper.countPending());
        stats.put("totalUsers", userMapper.countAll());
        stats.put("todayNew", queryCount("SELECT COUNT(*) FROM t_user WHERE DATE(create_time) = CURDATE()"));
        stats.put("approvedHouses", queryCount("SELECT COUNT(*) FROM t_house WHERE status = 'APPROVED'"));
        stats.put("rejectedHouses", queryCount("SELECT COUNT(*) FROM t_house WHERE status = 'REJECTED'"));
        stats.put("totalReviews", queryCount("SELECT COUNT(*) FROM t_landlord_review"));
        stats.put("pendingReviews", queryCount("SELECT COUNT(*) FROM t_landlord_review WHERE status = 'PENDING'"));
        stats.put("totalGuides", queryCount("SELECT COUNT(*) FROM t_rental_guide"));
        stats.put("publishedGuides", queryCount("SELECT COUNT(*) FROM t_rental_guide WHERE status = 'published'"));
        stats.put("totalContracts", queryCount("SELECT COUNT(*) FROM t_contract_review"));
        stats.put("totalFavorites", queryCount("SELECT COUNT(*) FROM t_house_favorite"));
        return Result.success(stats);
    }

    // ===== 评价管理 =====

    @GetMapping("/reviews")
    public Result<List<Map<String, Object>>> getReviews() {
        checkAdmin();
        String sql = """
                SELECT r.id, r.reviewer_id AS reviewerId, r.landlord_id AS landlordId,
                       r.house_id AS houseId, r.score, r.content, r.is_anonymous AS isAnonymous,
                       r.status, r.created_at AS createdAt,
                       IF(r.is_anonymous = 1, '匿名用户', COALESCE(reviewer.real_name, reviewer.username)) AS reviewerName,
                       COALESCE(landlord.real_name, landlord.username) AS landlordName,
                       h.title AS houseTitle
                FROM t_landlord_review r
                LEFT JOIN t_user reviewer ON r.reviewer_id = reviewer.id
                LEFT JOIN t_user landlord ON r.landlord_id = landlord.id
                LEFT JOIN t_house h ON r.house_id = h.id
                ORDER BY r.created_at DESC
                """;
        return Result.success(jdbcTemplate.queryForList(sql));
    }

    @PutMapping("/review/{id}/status")
    public Result<String> updateReviewStatus(@PathVariable Long id, @RequestBody Map<String, String> params) {
        checkAdmin();
        String status = params.getOrDefault("status", "APPROVED");
        flow.moderate("review",id,new HashMap<>(params));
        return Result.success("操作成功", null);
    }

    // ===== 攻略管理 =====

    @GetMapping("/guides")
    public Result<List<Map<String, Object>>> getGuides() {
        checkAdmin();
        String sql = """
                SELECT g.id, g.title, g.category, g.status, g.is_featured AS isFeatured,
                       g.view_count AS viewCount, g.like_count AS likeCount,
                       g.comment_count AS commentCount, g.created_at AS createdAt,
                       COALESCE(u.real_name, u.username) AS authorName
                FROM t_rental_guide g
                LEFT JOIN t_user u ON g.author_id = u.id
                ORDER BY g.created_at DESC
                """;
        return Result.success(jdbcTemplate.queryForList(sql));
    }

    @PutMapping("/guide/{id}/status")
    public Result<String> updateGuideStatus(@PathVariable Long id, @RequestBody Map<String, String> params) {
        checkAdmin();
        String status = params.getOrDefault("status", "published");
        flow.moderate("guide",id,new HashMap<>(params));
        return Result.success("操作成功", null);
    }

    // ===== 合同管理 =====

    @GetMapping("/contracts")
    public Result<List<Map<String, Object>>> getContracts() {
        checkAdmin();
        String sql = """
                SELECT c.id, c.user_id AS userId, c.house_id AS houseId, c.title,
                       c.overall_risk AS overallRisk, c.status, c.created_at AS createdAt,
                       COALESCE(u.real_name, u.username) AS userName,
                       h.title AS houseTitle
                FROM t_contract_review c
                LEFT JOIN t_user u ON c.user_id = u.id
                LEFT JOIN t_house h ON c.house_id = h.id
                ORDER BY c.created_at DESC
                """;
        return Result.success(jdbcTemplate.queryForList(sql));
    }

    // ===== 辅助方法 =====

    private Integer queryCount(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }
}
