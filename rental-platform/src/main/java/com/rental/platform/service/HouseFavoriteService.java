package com.rental.platform.service;

import com.rental.platform.entity.House;
import java.util.List;

/**
 * 房源收藏服务接口
 */
public interface HouseFavoriteService {

    /** 收藏房源 */
    boolean add(Long userId, Long houseId);

    /** 取消收藏 */
    boolean remove(Long userId, Long houseId);

    /** 是否已收藏 */
    boolean isFavorited(Long userId, Long houseId);

    /** 我的收藏列表 */
    List<House> listByUser(Long userId);
}
