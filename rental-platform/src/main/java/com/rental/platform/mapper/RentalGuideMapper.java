package com.rental.platform.mapper;

import com.rental.platform.entity.RentalGuide;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RentalGuideMapper {

    /**
     * 查询攻略列表（支持分类筛选和分页）
     */
    List<RentalGuide> findAll(@Param("category") String category,
                              @Param("offset") Integer offset,
                              @Param("size") Integer size);

    /**
     * 统计总数
     */
    int countAll(@Param("category") String category);

    /**
     * 根据ID查询攻略详情
     */
    RentalGuide findById(@Param("id") Long id);

    /**
     * 发布攻略
     */
    int insert(RentalGuide guide);

    /**
     * 增加浏览次数
     */
    int incrementViewCount(@Param("id") Long id);

    /**
     * 增加点赞数
     */
    int incrementLikeCount(@Param("id") Long id);

    /**
     * 增加评论数
     */
    int incrementCommentCount(@Param("id") Long id);
}

