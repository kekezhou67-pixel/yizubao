package com.rental.platform.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class GuideComment {
    private Long id;
    private Long guideId;
    private Long userId;
    private String content;
    private LocalDateTime createdAt;

    // 非数据库字段
    private String userName;
    private String userAvatar;
}

