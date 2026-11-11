-- ============================================================
-- AI 改造 · 阶段5：知识库表（RAG）
-- 修正#3：BIGINT AUTO_INCREMENT（与现有 8 张表风格一致，非雪花ID）
-- 执行：mysql -uroot -p rental_db < kb_tables.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS kb_document (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '文档ID',
    user_id     BIGINT                COMMENT '维护人（管理员）',
    title       VARCHAR(255) NOT NULL COMMENT '文档标题',
    source_type VARCHAR(20)  NOT NULL COMMENT 'LAW-法规 / GUIDE-攻略 / POLICY-政策',
    city        VARCHAR(50)           COMMENT '适用城市（空=全国）',
    content     LONGTEXT              COMMENT '原文',
    chunk_count INT          DEFAULT 0 COMMENT '分片数',
    status      TINYINT      DEFAULT 1 COMMENT '1正常 0删除',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_type_city (source_type, city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档';

CREATE TABLE IF NOT EXISTS kb_chunk (
    id           BIGINT   PRIMARY KEY AUTO_INCREMENT COMMENT '分片ID',
    doc_id       BIGINT   NOT NULL        COMMENT '所属文档',
    chunk_index  INT      NOT NULL        COMMENT '序号',
    parent_text  TEXT                     COMMENT '父块（喂给 LLM，约2000字）',
    child_text   TEXT     NOT NULL        COMMENT '子块（用于检索，约500字）',
    vector_id    VARCHAR(128)             COMMENT 'FAISS/ES 中的向量ID',
    token_count  INT                      COMMENT 'Token 数',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doc (doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档分片（父子分片：child检索命中→parent喂LLM）';
