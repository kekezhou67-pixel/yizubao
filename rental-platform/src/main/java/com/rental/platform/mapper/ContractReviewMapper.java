package com.rental.platform.mapper;

import com.rental.platform.entity.ContractReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ContractReviewMapper {

    int insert(ContractReview review);

    ContractReview findById(@Param("id") Long id);

    int update(ContractReview review);

    int delete(@Param("id") Long id);

    List<ContractReview> listByUser(@Param("userId") Long userId);

    int updateHouseId(@Param("id") Long id, @Param("houseId") Long houseId);
}

