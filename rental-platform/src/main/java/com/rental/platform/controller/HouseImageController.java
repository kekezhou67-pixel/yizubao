package com.rental.platform.controller;

import com.rental.platform.config.UserContextHolder;
import com.rental.platform.service.HouseImageSearchService;
import com.rental.platform.utils.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 多模态图搜接口（AI 改造 · 阶段7B，工程方案 §5.2）
 *
 * - POST /api/house/search-by-image  以图搜房（multipart: file, topK）
 * - POST /api/house/search-by-text   文字描述找房（文→图跨模态）
 * - POST /api/house/clip/reindex     批量入库房源图（管理员）
 */
@RestController
@RequestMapping("/api/house")
public class HouseImageController {

    private static final Logger log = LoggerFactory.getLogger(HouseImageController.class);

    private final HouseImageSearchService houseImageSearchService;

    public HouseImageController(HouseImageSearchService houseImageSearchService) {
        this.houseImageSearchService = houseImageSearchService;
    }

    /** 以图搜房：上传照片 → 相似房源（图→图） */
    @PostMapping("/search-by-image")
    public Result<List<Map<String, Object>>> searchByImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "10") int topK) {
        if (file.isEmpty()) {
            return Result.error("请上传图片");
        }
        String filename = file.getOriginalFilename();
        String lower = filename != null ? filename.toLowerCase() : "";
        if (!lower.endsWith(".jpg") && !lower.endsWith(".jpeg") && !lower.endsWith(".png")
                && !lower.endsWith(".webp") && !lower.endsWith(".bmp")) {
            return Result.error("仅支持 jpg/png/webp/bmp 图片");
        }
        try {
            List<Map<String, Object>> results =
                    houseImageSearchService.searchByImage(filename, file.getBytes(), topK);
            return Result.success(results);
        } catch (Exception e) {
            log.error("以图搜房失败: {}", e.getMessage(), e);
            return Result.error("以图搜房失败: " + e.getMessage());
        }
    }

    /** 文字描述找房：如"落地窗原木风"（文→图跨模态） */
    @PostMapping("/search-by-text")
    public Result<List<Map<String, Object>>> searchByText(@RequestBody Map<String, Object> body) {
        String description = body.get("description") != null
                ? body.get("description").toString() : "";
        int topK = body.get("topK") != null ? Integer.parseInt(body.get("topK").toString()) : 10;
        if (description.isBlank()) {
            return Result.error("description 不能为空");
        }
        try {
            return Result.success(houseImageSearchService.searchByText(description.trim(), topK));
        } catch (Exception e) {
            log.error("文字搜图失败: {}", e.getMessage(), e);
            return Result.error("文字搜图失败: " + e.getMessage());
        }
    }

    /** 批量入库房源封面图到 CLIP 索引（管理员，首次部署/图片更新后执行） */
    @PostMapping("/clip/reindex")
    public Result<Map<String, Object>> reindex(
            @RequestParam(defaultValue = "500") int limit) {
        if (!UserContextHolder.hasRole("ADMIN")) {
            return Result.error(403, "仅管理员可执行入库");
        }
        try {
            return Result.success(houseImageSearchService.reindex(limit));
        } catch (Exception e) {
            log.error("CLIP 入库失败: {}", e.getMessage(), e);
            return Result.error("CLIP 入库失败: " + e.getMessage());
        }
    }
}
