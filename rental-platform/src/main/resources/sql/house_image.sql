-- ============================================================
-- AI 改造 · 阶段7A/7B：房源图片表（OCR文本 + CLIP向量 + caption）
-- 修正#3：BIGINT AUTO_INCREMENT（与现有表一致）
-- ============================================================

CREATE TABLE IF NOT EXISTS house_image (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '图片ID',
    house_id    BIGINT       NOT NULL        COMMENT '所属房源',
    image_url   VARCHAR(500) NOT NULL        COMMENT '图片地址',
    caption     VARCHAR(500)                 COMMENT '图片描述（CLIP 文字检索靠它 —— 讲义05：图片没文字，靠caption被文字命中）',
    vector_id   VARCHAR(128)                 COMMENT 'CLIP 向量ID（img 索引）',
    ocr_text    TEXT                         COMMENT 'OCR 识别出的文字（合同扫描件用）',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_house (house_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房源图片（多模态）';
