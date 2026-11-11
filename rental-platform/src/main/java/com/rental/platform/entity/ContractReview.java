package com.rental.platform.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ContractReview {
    private Long id;
    private Long userId;
    private Long houseId;
    private String title;
    private String imageUrl;
    private String contentText;
    private String aiAnalysis;
    private String riskPoints;
    private String overallRisk;
    private String status;
    private LocalDateTime createdAt;
}

