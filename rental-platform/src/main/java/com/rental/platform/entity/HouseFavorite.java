package com.rental.platform.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class HouseFavorite {
    private Long id;
    private Long userId;
    private Long houseId;
    private LocalDateTime createdAt;
}

