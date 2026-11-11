package com.rental.platform.mapper;

import com.rental.platform.entity.LandlordReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LandlordReviewMapper {

    /**
     * 提交评价
     */
    int insert(LandlordReview review);

    /**
     * 查询房东的所有评价（含评论者信息）
     */
    List<LandlordReview> findByLandlord(@Param("landlordId") Long landlordId);

    /**
     * 根据ID查询评价
     */
    LandlordReview findById(@Param("id") Long id);

    /**
     * 查询房东平均评分
     */
    Double findAvgScore(@Param("landlordId") Long landlordId);
}

