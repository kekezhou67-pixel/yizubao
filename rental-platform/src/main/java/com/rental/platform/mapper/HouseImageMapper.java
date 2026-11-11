package com.rental.platform.mapper;

import com.rental.platform.entity.HouseImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HouseImageMapper {

    int insert(HouseImage image);

    HouseImage findById(@Param("id") Long id);

    List<HouseImage> listByHouse(@Param("houseId") Long houseId);

    /** 按 CLIP 向量ID 反查图片（以图搜房结果 → 房源） */
    List<HouseImage> findByVectorIds(@Param("vectorIds") List<String> vectorIds);

    int updateVector(@Param("id") Long id,
                     @Param("vectorId") String vectorId,
                     @Param("caption") String caption);

    int deleteByHouseId(@Param("houseId") Long houseId);
}
