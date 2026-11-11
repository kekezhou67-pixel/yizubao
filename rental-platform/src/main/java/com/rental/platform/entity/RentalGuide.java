package com.rental.platform.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RentalGuide {
    private Long id;
    private Long authorId;
    private String title;
    private String content;
    private String category;
    private String coverImage;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer isFeatured;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 非数据库字段
    private String authorName;
    private String authorAvatar;
}

