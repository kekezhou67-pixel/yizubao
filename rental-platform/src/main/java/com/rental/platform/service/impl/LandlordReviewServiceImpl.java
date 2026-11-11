package com.rental.platform.service.impl;

import com.rental.platform.entity.LandlordReview;
import com.rental.platform.mapper.LandlordReviewMapper;
import com.rental.platform.service.LandlordReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LandlordReviewServiceImpl implements LandlordReviewService {

    private final LandlordReviewMapper landlordReviewMapper;
    private final com.rental.platform.mapper.HouseMapper houses;

    @Autowired
    public LandlordReviewServiceImpl(LandlordReviewMapper landlordReviewMapper, com.rental.platform.mapper.HouseMapper houses) {
        this.houses=houses;
        this.landlordReviewMapper = landlordReviewMapper;
    }

    @Override
    public boolean submitReview(LandlordReview review) {
        var house=houses.findById(review.getHouseId());
        if(house==null||!"APPROVED".equals(house.getStatus()))throw new com.rental.platform.exception.BusinessException("房源未公开");
        long uid=com.rental.platform.config.UserContextHolder.getCurrentUserId();
        if(house.getLandlordId().equals(uid))throw new com.rental.platform.exception.BusinessException("不能评价本人房源");
        if(review.getScore()==null||review.getScore()<1||review.getScore()>5||review.getContent()==null||review.getContent().isBlank()||review.getContent().length()>2000)throw new com.rental.platform.exception.BusinessException("请填写1至5分及不超过2000字的评价");
        review.setReviewerId(uid);review.setLandlordId(house.getLandlordId());review.setStatus("PENDING");
        return landlordReviewMapper.insert(review) > 0;
    }

    @Override
    public List<LandlordReview> getLandlordReviews(Long landlordId) {
        return landlordReviewMapper.findByLandlord(landlordId);
    }

    @Override
    public double getAvgScore(Long landlordId) {
        Double avg = landlordReviewMapper.findAvgScore(landlordId);
        return avg != null ? avg : 0.0;
    }
}

