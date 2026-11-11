package com.rental.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 房源 VO —— 用于前端展示，不暴露敏感信息（房东ID、联系电话等）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HouseVO {

    private Long id;
    private String title;
    private String description;
    private String city;
    private String district;
    private String address;
    private Long landlordId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal price;
    private BigDecimal area;
    private Integer bedrooms;
    private Integer livingRooms;
    private Integer bathrooms;
    private String floorInfo;
    private String orientation;
    private String decoration;
    private String facilities;
    private Long subwayStationId;
    private Integer distanceToSubway;
    private String coverImage;
    private String images;
    private Integer viewCount;
    private String status;
    private LocalDateTime createdAt;

    // 关联信息
    private String landlordName;
    private String subwayStationName;
    private String subwayLineName;
}
