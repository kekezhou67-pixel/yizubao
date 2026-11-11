package com.rental.platform.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LandlordReview {
    private Long id;
    private Long reviewerId;
    private Long landlordId;
    private Long houseId;
    private Integer score;
    private String content;
    private Integer isAnonymous;
    private String status;
    private LocalDateTime createdAt;

    // 非数据库字段
    private String reviewerName;
    private String reviewerAvatar;
    private String houseTitle;
}

