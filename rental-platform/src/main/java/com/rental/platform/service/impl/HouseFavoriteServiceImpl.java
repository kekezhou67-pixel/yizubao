package com.rental.platform.service.impl;

import com.rental.platform.entity.House;
import com.rental.platform.entity.HouseFavorite;
import com.rental.platform.exception.BusinessException;
import com.rental.platform.mapper.HouseFavoriteMapper;
import com.rental.platform.mapper.HouseMapper;
import com.rental.platform.service.HouseFavoriteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 房源收藏服务实现 —— 构造器注入 + 事务管理
 */
@Service
public class HouseFavoriteServiceImpl implements HouseFavoriteService {

    private final HouseFavoriteMapper houseFavoriteMapper;
    private final HouseMapper houseMapper;

    // ✅ 构造器注入
    public HouseFavoriteServiceImpl(HouseFavoriteMapper houseFavoriteMapper,
                                     HouseMapper houseMapper) {
        this.houseFavoriteMapper = houseFavoriteMapper;
        this.houseMapper = houseMapper;
    }

    @Override
    @Transactional
    public boolean add(Long userId, Long houseId) {
        // 检查房源是否存在
        if (houseMapper.findById(houseId) == null) {
            throw BusinessException.notFound("房源不存在");
        }
        // 检查是否已收藏
        if (houseFavoriteMapper.isFavorited(userId, houseId) > 0) {
            return false;  // 已收藏，不算错误，幂等返回
        }

        HouseFavorite favorite = new HouseFavorite();
        favorite.setUserId(userId);
        favorite.setHouseId(houseId);
        return houseFavoriteMapper.insert(favorite) > 0;
    }

    @Override
    @Transactional
    public boolean remove(Long userId, Long houseId) {
        return houseFavoriteMapper.delete(userId, houseId) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFavorited(Long userId, Long houseId) {
        return houseFavoriteMapper.isFavorited(userId, houseId) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<House> listByUser(Long userId) {
        return houseFavoriteMapper.listByUser(userId);
    }
}
