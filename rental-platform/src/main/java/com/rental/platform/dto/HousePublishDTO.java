package com.rental.platform.dto;

import lombok.Data;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 房源发布 DTO —— 前端提交的发布请求参数
 * 与实体分离，便于前后端数据格式解耦
 */
@Data
public class HousePublishDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过200字")
    private String title;

    private String description;

    @NotBlank(message = "城市不能为空")
    private String city;

    private String district;

    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    private BigDecimal price;

    @NotNull(message = "面积不能为空")
    @DecimalMin(value = "1.0", message = "面积必须大于0")
    private BigDecimal area;

    @NotNull(message = "卧室数量不能为空")
    @Min(value = 0, message = "卧室数量不能为负数")
    private Integer bedrooms;

    @Min(value = 0, message = "客厅数量不能为负数")
    private Integer livingRooms;

    @Min(value = 0, message = "卫生间数量不能为负数")
    private Integer bathrooms;

    private String floorInfo;
    private String orientation;
    private String decoration;
    private String facilities;

    private Long subwayStationId;
    private Integer distanceToSubway;

    private String coverImage;
    private String images;

    private String contactPhone;
}
