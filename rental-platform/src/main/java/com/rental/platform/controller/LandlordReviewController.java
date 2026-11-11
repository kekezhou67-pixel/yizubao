package com.rental.platform.controller;

import com.rental.platform.config.UserContextHolder;
import com.rental.platform.entity.LandlordReview;
import com.rental.platform.service.LandlordReviewService;
import com.rental.platform.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/landlord-review")
public class LandlordReviewController {

    private final LandlordReviewService landlordReviewService;

    @Autowired
    public LandlordReviewController(LandlordReviewService landlordReviewService) {
        this.landlordReviewService = landlordReviewService;
    }

    /**
     * 提交房东评价
     */
    @PostMapping("/submit")
    public Result<String> submit(@RequestBody Map<String, Object> params) {
        Long landlordId = Long.valueOf(params.get("landlordId").toString());
        Long houseId = Long.valueOf(params.get("houseId").toString());
        Integer score = Integer.valueOf(params.get("score").toString());
        String content = (String) params.get("content");
        Integer isAnonymous = params.get("isAnonymous") != null ? Integer.valueOf(params.get("isAnonymous").toString()) : 0;

        LandlordReview review = new LandlordReview();
        review.setReviewerId(UserContextHolder.getCurrentUserId());
        review.setLandlordId(landlordId);
        review.setHouseId(houseId);
        review.setScore(score);
        review.setContent(content);
        review.setIsAnonymous(isAnonymous);

        boolean success = landlordReviewService.submitReview(review);
        if (success) {
            return Result.success("评价成功", null);
        }
        return Result.error("评价失败");
    }

    /**
     * 获取房东评价列表（含平均分）
     */
    @GetMapping("/landlord/{landlordId}")
    public Result<Map<String, Object>> landlordReviews(@PathVariable Long landlordId) {
        List<LandlordReview> reviews = landlordReviewService.getLandlordReviews(landlordId);
        double avgScore = landlordReviewService.getAvgScore(landlordId);

        Map<String, Object> result = new HashMap<>();
        result.put("avgScore", Math.round(avgScore * 10.0) / 10.0);
        result.put("reviewCount", reviews.size());
        result.put("reviews", reviews);

        return Result.success(result);
    }
}

