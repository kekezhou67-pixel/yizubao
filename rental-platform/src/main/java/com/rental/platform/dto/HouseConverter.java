package com.rental.platform.dto;

import com.rental.platform.entity.House;

/**
 * 房源数据转换工具 —— 集中管理 Entity / DTO / VO 之间的转换逻辑
 * <p>
 * 使用静态方法而非 MapStruct，简单直观、零依赖。
 * 如果项目规模变大再引入 MapStruct。
 */
public class HouseConverter {

    /** HousePublishDTO → House Entity */
    public static House toEntity(HousePublishDTO dto, Long landlordId) {
        House house = new House();
        house.setLandlordId(landlordId);
        house.setTitle(dto.getTitle());
        house.setDescription(dto.getDescription());
        house.setCity(dto.getCity());
        house.setDistrict(dto.getDistrict());
        house.setAddress(dto.getAddress());
        house.setLatitude(dto.getLatitude());
        house.setLongitude(dto.getLongitude());
        house.setPrice(dto.getPrice());
        house.setArea(dto.getArea());
        house.setBedrooms(dto.getBedrooms());
        house.setLivingRooms(dto.getLivingRooms());
        house.setBathrooms(dto.getBathrooms());
        house.setFloorInfo(dto.getFloorInfo());
        house.setOrientation(dto.getOrientation());
        house.setDecoration(dto.getDecoration());
        house.setFacilities(dto.getFacilities());
        house.setSubwayStationId(dto.getSubwayStationId());
        house.setDistanceToSubway(dto.getDistanceToSubway());
        house.setCoverImage(dto.getCoverImage());
        house.setImages(dto.getImages());
        house.setContactPhone(dto.getContactPhone());
        house.setStatus("PENDING");   // 发布后默认待审核
        house.setViewCount(0);
        return house;
    }

    /** House Entity → HouseVO（脱敏后的前端展示对象） */
    public static HouseVO toVO(House entity) {
        if (entity == null) {
            return null;
        }
        return HouseVO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .city(entity.getCity())
                .district(entity.getDistrict())
                .address(entity.getAddress())
                .landlordId(entity.getLandlordId())
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .price(entity.getPrice())
                .area(entity.getArea())
                .bedrooms(entity.getBedrooms())
                .livingRooms(entity.getLivingRooms())
                .bathrooms(entity.getBathrooms())
                .floorInfo(entity.getFloorInfo())
                .orientation(entity.getOrientation())
                .decoration(entity.getDecoration())
                .facilities(entity.getFacilities())
                .subwayStationId(entity.getSubwayStationId())
                .distanceToSubway(entity.getDistanceToSubway())
                .coverImage(entity.getCoverImage())
                .images(entity.getImages())
                .viewCount(entity.getViewCount())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .landlordName(entity.getLandlordName())
                .subwayStationName(entity.getSubwayStationName())
                .subwayLineName(entity.getSubwayLineName())
                .build();
    }
}
