package com.rental.platform.mapper;

import com.rental.platform.entity.SubwayStation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SubwayStationMapper {

    List<String> findCities();

    List<String> findLinesByCity(@Param("city") String city);

    List<SubwayStation> findByCityAndLine(@Param("city") String city, @Param("lineName") String lineName);
}

