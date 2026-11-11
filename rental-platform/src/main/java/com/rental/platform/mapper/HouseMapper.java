package com.rental.platform.mapper;

import com.rental.platform.entity.House;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface HouseMapper {

    /**
     * 查询房源列表，支持多条件动态筛选、排序和分页
     * params 需要包含 offset 和 limit
     */
    List<House> findAll(Map<String, Object> params);

    /**
     * 统计符合条件的房源总数（与 findAll 条件一致）
     */
    long count(Map<String, Object> params);

    /**
     * 根据ID查询房源详情（含关联信息）
     */
    House findById(@Param("id") Long id);

    /**
     * 新增房源
     */
    int insert(House house);

    /**
     * 更新房源
     */
    int update(House house);

    /**
     * 删除房源
     */
    int delete(@Param("id") Long id);

    /**
     * 增加浏览次数
     */
    int incrementViewCount(@Param("id") Long id);

    /**
     * 修改房源状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 查询地铁站附近的房源
     */
    List<House> findBySubwayStation(@Param("stationId") Long stationId, @Param("maxDistance") Integer maxDistance);

    @Select("SELECT COUNT(*) FROM t_house")
    int countAll();

    @Select("SELECT COUNT(*) FROM t_house WHERE status = 'PENDING'")
    int countPending();
}

