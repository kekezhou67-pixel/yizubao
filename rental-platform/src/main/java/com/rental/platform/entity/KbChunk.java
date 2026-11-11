package com.rental.platform.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分片（AI 改造 · 阶段5，父子分片结构 —— 讲义04 §3）
 * child 用于向量/BM25 检索（精度），parent 喂给 LLM（上下文完整）
 * 对应表 kb_chunk
 */
@Data
public class KbChunk {
    private Long id;
    private Long docId;
    private Integer chunkIndex;
    /** 父块（约2000字，喂给 LLM） */
    private String parentText;
    /** 子块（约500字，用于检索） */
    private String childText;
    /** FAISS/ES 中的向量ID */
    private String vectorId;
    private Integer tokenCount;
    private LocalDateTime createTime;
}
