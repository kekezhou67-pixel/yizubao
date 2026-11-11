package com.rental.platform.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 合同条款拆分器（AI 改造 · 阶段6，工程方案 §6.1）
 *
 * 按"第X条"（中文数字或阿拉伯数字）切分合同文本，
 * 每个条款独立检索法条、独立判断风险 —— 这是"每条风险带法条依据"的前提。
 *
 * 兜底：文本没有"第X条"结构时，整段作为单条款审查。
 */
@Component
public class ClauseSplitter {

    /** 前瞻分割：在"第X条"前切开，保留条款编号 */
    private static final String CLAUSE_PATTERN = "(?=第[一二三四五六七八九十百零\\d]+条)";

    /** 单次审查条款上限（防止超长合同把 LLM 调用次数打爆） */
    private static final int MAX_CLAUSES = 15;

    public List<String> split(String contractText) {
        if (contractText == null || contractText.isBlank()) {
            return List.of();
        }
        String[] parts = contractText.split(CLAUSE_PATTERN);
        List<String> clauses = Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> s.length() > 10)     // 过滤标题碎片
                .limit(MAX_CLAUSES)
                .toList();
        if (clauses.isEmpty()) {
            // 兜底：没有条款结构，整段审查
            List<String> fallback = new ArrayList<>();
            fallback.add(contractText.trim());
            return fallback;
        }
        return clauses;
    }
}
