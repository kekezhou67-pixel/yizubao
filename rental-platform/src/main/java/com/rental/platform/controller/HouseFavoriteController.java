package com.rental.platform.controller;

import com.rental.platform.config.UserContextHolder;
import com.rental.platform.entity.House;
import com.rental.platform.service.HouseFavoriteService;
import com.rental.platform.utils.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 房源收藏 Controller —— 使用 Service 层
 */
@RestController
@RequestMapping("/api/favorite")
public class HouseFavoriteController {

    private final HouseFavoriteService houseFavoriteService;

    // ✅ 构造器注入
    public HouseFavoriteController(HouseFavoriteService houseFavoriteService) {
        this.houseFavoriteService = houseFavoriteService;
    }

    /** 添加收藏 */
    @PostMapping("/add")
    public Result<String> add(@RequestBody Map<String, Object> params) {
        Long userId = UserContextHolder.getCurrentUserId();
        Long houseId = Long.valueOf(params.get("houseId").toString());

        boolean success = houseFavoriteService.add(userId, houseId);
        return success ? Result.success("收藏成功", null) : Result.error("已收藏过该房源");
    }

    /** 取消收藏 */
    @PostMapping("/remove")
    public Result<String> remove(@RequestBody Map<String, Object> params) {
        Long userId = UserContextHolder.getCurrentUserId();
        Long houseId = Long.valueOf(params.get("houseId").toString());

        boolean success = houseFavoriteService.remove(userId, houseId);
        return success ? Result.success("取消收藏成功", null) : Result.error("取消收藏失败");
    }

    /** 检查是否已收藏 */
    @GetMapping("/check/{houseId}")
    public Result<Boolean> check(@PathVariable Long houseId) {
        boolean favorited = houseFavoriteService.isFavorited(
                UserContextHolder.getCurrentUserId(), houseId);
        return Result.success(favorited);
    }

    /** 我的收藏列表 */
    @GetMapping("/list")
    public Result<List<House>> list() {
        return Result.success(houseFavoriteService.listByUser(UserContextHolder.getCurrentUserId()));
    }
}
