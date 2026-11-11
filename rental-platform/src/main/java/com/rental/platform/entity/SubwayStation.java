package com.rental.platform.entity;

import lombok.Data;

@Data
public class SubwayStation {
    private Long id;
    private String city;
    private String lineName;
    private String name;
    private Integer sortOrder;
}

