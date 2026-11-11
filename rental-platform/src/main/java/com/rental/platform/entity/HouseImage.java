package com.rental.platform.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 房源图片（AI 改造 · 阶段7A/7B，多模态）
 * caption 是文字检索命中图片的关键（讲义05：图片本身没文字）
 */
@Data
public class HouseImage {
    private Long id;
    private Long houseId;
    private String imageUrl;
    /** 图片描述（CLIP 文搜图靠它） */
    private String caption;
    /** CLIP 向量ID */
    private String vectorId;
    /** OCR 识别文字（合同扫描件用） */
    private String ocrText;
    private LocalDateTime createTime;
}
