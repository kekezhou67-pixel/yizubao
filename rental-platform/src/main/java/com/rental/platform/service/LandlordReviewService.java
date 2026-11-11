package com.rental.platform.service;

import com.rental.platform.entity.LandlordReview;
import java.util.List;

public interface LandlordReviewService {

    /**
     * 提交房东评价
     */
    boolean submitReview(LandlordReview review);

    /**
     * 获取房东评价列表（含平均分）
     */
    List<LandlordReview> getLandlordReviews(Long landlordId);

    /**
     * 计算房东平均评分
     */
    double getAvgScore(Long landlordId);
}

