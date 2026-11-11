package com.rental.platform.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档（AI 改造 · 阶段5）
 * 对应表 kb_document（建表 SQL：resources/sql/kb_tables.sql）
 */
@Data
public class KbDocument {
    private Long id;
    /** 维护人（管理员） */
    private Long userId;
    private String title;
    /** LAW-法规 / GUIDE-攻略 / POLICY-政策 */
    private String sourceType;
    /** 适用城市（空=全国） */
    private String city;
    /** 原文 */
    private String content;
    private Integer chunkCount;
    /** 1正常 0删除 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
