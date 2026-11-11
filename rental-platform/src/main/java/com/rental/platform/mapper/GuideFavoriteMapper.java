package com.rental.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GuideFavoriteMapper {

    /**
     * 收藏攻略
     */
    int insert(@Param("userId") Long userId, @Param("guideId") Long guideId);

    /**
     * 取消收藏
     */
    int delete(@Param("userId") Long userId, @Param("guideId") Long guideId);

    /**
     * 检查是否已收藏
     */
    int isFavorited(@Param("userId") Long userId, @Param("guideId") Long guideId);

    /**
     * 用户收藏列表
     */
    List<Long> listByUser(@Param("userId") Long userId);
}

