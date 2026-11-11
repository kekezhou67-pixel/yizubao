package com.rental.platform.mapper;

import com.rental.platform.entity.HouseFavorite;
import com.rental.platform.entity.House;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HouseFavoriteMapper {

    /**
     * 添加收藏
     */
    int insert(HouseFavorite favorite);

    /**
     * 取消收藏
     */
    int delete(@Param("userId") Long userId, @Param("houseId") Long houseId);

    /**
     * 检查是否已收藏
     */
    int isFavorited(@Param("userId") Long userId, @Param("houseId") Long houseId);

    /**
     * 查询用户收藏列表（关联房源信息）
     */
    List<House> listByUser(@Param("userId") Long userId);
}

