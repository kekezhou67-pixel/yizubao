package com.rental.platform.controller;

import com.rental.platform.config.UserContextHolder;
import com.rental.platform.entity.GuideComment;
import com.rental.platform.entity.RentalGuide;
import com.rental.platform.mapper.GuideCommentMapper;
import com.rental.platform.service.RentalGuideService;
import com.rental.platform.utils.Result;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 租房攻略 Controller —— 使用 Service 层 + 构造器注入
 */
@RestController
@RequestMapping("/api/guide")
public class RentalGuideController {

    private final RentalGuideService rentalGuideService;
    private final GuideCommentMapper guideCommentMapper;

    // ✅ 构造器注入（Service 负责写操作，部分读操作直接走 Mapper）
    public RentalGuideController(RentalGuideService rentalGuideService,
                                  GuideCommentMapper guideCommentMapper) {
        this.rentalGuideService = rentalGuideService;
        this.guideCommentMapper = guideCommentMapper;
    }

    /** 攻略列表（分页+分类筛选） */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "") String category,
                                            @RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "10") Integer size) {
        int offset = (page - 1) * size;
        List<RentalGuide> guides = rentalGuideService.findAll(category, offset, size);
        int total = rentalGuideService.countAll(category);

        Map<String, Object> result = new HashMap<>();
        result.put("list", guides);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return Result.success(result);
    }

    /** 攻略详情（浏览量+1） */
    @GetMapping("/detail/{id}")
    public Result<RentalGuide> detail(@PathVariable Long id) {
        return Result.success(rentalGuideService.getDetail(id));
    }

    /** 发布攻略 */
    @PostMapping("/publish")
    public Result<RentalGuide> publish(@RequestBody Map<String, Object> params) {
        String title = (String) params.get("title");
        String content = (String) params.get("content");
        String category = (String) params.get("category");
        String coverImage = (String) params.get("coverImage");

        RentalGuide guide = new RentalGuide();
        guide.setAuthorId(UserContextHolder.getCurrentUserId());
        guide.setTitle(title);
        guide.setContent(content);
        guide.setCategory(category);
        guide.setCoverImage(coverImage);

        boolean success = rentalGuideService.publish(guide);
        return success ? Result.success("发布成功", guide) : Result.error("发布失败");
    }

    /** 点赞攻略 */
    @PostMapping("/like/{id}")
    public Result<String> like(@PathVariable Long id) {
        Long userId = UserContextHolder.getCurrentUserId();
        rentalGuideService.like(id, userId);
        return Result.success("点赞成功", null);
    }

    /** 发表评论 */
    @PostMapping("/comment")
    public Result<String> comment(@RequestBody Map<String, Object> params) {
        Long guideId = Long.valueOf(params.get("guideId").toString());
        String content = (String) params.get("content");

        Long userId = UserContextHolder.getCurrentUserId();
        rentalGuideService.addComment(guideId, userId, content);
        return Result.success("评论成功", null);
    }

    /** 攻略评论列表 */
    @GetMapping("/comments/{guideId}")
    public Result<List<GuideComment>> comments(@PathVariable Long guideId) {
        return Result.success(guideCommentMapper.findByGuideId(guideId));
    }
}
