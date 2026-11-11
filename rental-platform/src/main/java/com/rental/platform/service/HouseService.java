package com.rental.platform.service;

import com.rental.platform.dto.HouseVO;
import com.rental.platform.dto.PageResult;
import com.rental.platform.entity.House;

import java.util.List;
import java.util.Map;

/**
 * 房源服务接口
 */
public interface HouseService {

    /**
     * 分页查询房源（支持动态筛选和排序）
     */
    PageResult<HouseVO> findAll(Map<String, Object> params, int page, int size);

    /**
     * 房源详情（含浏览次数+1）
     */
    HouseVO getDetail(Long id);

    /**
     * 按ID查询（不增加浏览数）
     */
    House findById(Long id);

    /**
     * 发布房源（需业务校验 + 事务）
     * @param house 房源实体（landlordId 已在调用前设置）
     */
    House publish(House house);

    /**
     * 更新房源
     */
    boolean update(House house);

    /**
     * 删除房源（仅限房东本人或管理员）
     */
    boolean delete(Long id, Long operatorId, String operatorRole);

    /**
     * 修改房源状态（仅限管理员）
     */
    boolean changeStatus(Long id, String status, String operatorRole);

    /**
     * 按地铁站查询附近房源
     */
    List<HouseVO> findBySubwayStation(Long stationId, Integer maxDistance);
}
