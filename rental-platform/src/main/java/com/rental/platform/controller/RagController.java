package com.rental.platform.controller;

import com.rental.platform.ai.RagService;
import com.rental.platform.utils.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * RAG 政策问答接口（AI 改造 · 阶段5，工程方案 §5.2/§5.3）
 *
 * POST /api/ai/qa  {"question":"杭州租房押金一般收几个月"}
 * → {"answer":"...【资料1】", "sources":[{text, score, docTitle, docId}]}
 *
 * 验收标准（工程方案 §9.1）：
 * - 知识库内问题 → 带法条引用回答
 * - 知识库外问题 → 明确回答"资料中没有相关信息"（防幻觉）
 */
@RestController
@RequestMapping("/api/ai")
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/qa")
    public Result<Map<String, Object>> qa(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        if (question == null || question.isBlank()) {
            return Result.error("question 不能为空");
        }
        try {
            RagService.QaResult r = ragService.answer(question.trim());
            List<Map<String, Object>> sources = r.sources().stream()
                    .map(s -> Map.<String, Object>of(
                            "text", s.text(),
                            "score", s.score(),
                            "docTitle", s.docTitle(),
                            "docId", s.docId() != null ? s.docId() : 0))
                    .toList();
            return Result.success(Map.of(
                    "answer", r.answer(),
                    "sources", sources));
        } catch (Exception e) {
            log.error("RAG 问答失败: {}", e.getMessage(), e);
            return Result.error("问答失败: " + e.getMessage());
        }
    }
}
