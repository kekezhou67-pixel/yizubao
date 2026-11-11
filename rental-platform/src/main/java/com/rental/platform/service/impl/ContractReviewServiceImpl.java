package com.rental.platform.service.impl;

import com.alibaba.fastjson.JSON;
import com.rental.platform.ai.ClauseSplitter;
import com.rental.platform.ai.PromptTemplates;
import com.rental.platform.ai.RagService;
import com.rental.platform.entity.ContractReview;
import com.rental.platform.mapper.ContractReviewMapper;
import com.rental.platform.service.ContractReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 合同审查服务（AI 改造 · 阶段6：RAG 化 —— 每条风险带法条依据）
 *
 * 旧版问题（技术债#5）：直接让 LLM 凭参数记忆审合同 → 编造法条。
 * 新版流程（工程方案 §6.2）：
 *   ① ClauseSplitter 按"第X条"拆条款
 *   ② 每条条款 → RagService 混合检索相关法条 Top-3（FAISS+BM25+RRF+精排）
 *   ③ 带法条的 Prompt → .entity(ClauseRisk) 结构化判断（禁止编造：只能引用检索到的法条）
 *   ④ 汇总 overallRisk + LLM 生成 summary
 *   ⑤ 存 t_contract_review（risk_points 存含 law/source 的标准 JSON，不改表结构）
 *
 * 响应向后兼容：保留 {id,title,keyClauses,riskPoints,overallRisk,summary,createdAt}，
 * riskPoints 每项新增 law/source 字段，顶层新增 sources（工程方案 §5.1）。
 */
@Service
public class ContractReviewServiceImpl implements ContractReviewService {

    private static final Logger log = LoggerFactory.getLogger(ContractReviewServiceImpl.class);

    private final ChatClient chatClient;
    private final ContractReviewMapper contractReviewMapper;
    private final ClauseSplitter clauseSplitter;
    private final RagService ragService;

    public ContractReviewServiceImpl(@Lazy ChatClient chatClient,   // @Lazy 打破循环依赖（MCP toolResolver 链）
                                     ContractReviewMapper contractReviewMapper,
                                     ClauseSplitter clauseSplitter,
                                     RagService ragService) {
        this.chatClient = chatClient;
        this.contractReviewMapper = contractReviewMapper;
        this.clauseSplitter = clauseSplitter;
        this.ragService = ragService;
    }

    // ==================== 结构化输出 DTO ====================

    /** 单条款风险判定（字段名即 Prompt 要求的 JSON 键） */
    public record ClauseRisk(
            boolean risk,          // 是否有风险
            String riskDesc,       // 风险描述
            String severity,       // 高/中/低
            String law,            // 引用的法条内容（必须来自检索资料）
            String source,         // 法条来源文档名
            String suggestion) {}  // 修改建议

    /** 总体审查意见 */
    public record ReviewSummary(String title, String summary) {}

    // ==================== 主流程 ====================

    @Override
    public Map<String, Object> reviewContract(String contentText, Long userId) {
        try {
            // ① 拆条款
            List<String> clauses = clauseSplitter.split(contentText);
            log.info("合同审查开始：拆出 {} 个条款", clauses.size());

            List<Map<String, Object>> riskPoints = new ArrayList<>();
            List<Map<String, Object>> keyClauses = new ArrayList<>();
            List<Map<String, Object>> sources = new ArrayList<>();
            LinkedHashSet<String> seenSources = new LinkedHashSet<>();
            boolean hasHigh = false, hasMedium = false;

            // ②③ 逐条款：检索法条 → 带法条判断
            for (String clause : clauses) {
                List<RagService.RetrievedChunk> laws;
                try {
                    laws = ragService.retrieve(clause, 3);
                } catch (Exception e) {
                    log.warn("条款检索法条失败（降级为无法条审查）: {}", e.getMessage());
                    laws = List.of();
                }

                String prompt = PromptTemplates.CONTRACT_CLAUSE_REVIEW
                        .replace("{laws}", formatLaws(laws))
                        .replace("{clause}", clause);

                ClauseRisk r;
                try {
                    r = chatClient.prompt()
                            .system("你是租房合同审查律师，严格按 JSON 输出判定结果。")
                            .user(prompt)
                            .call()
                            .entity(ClauseRisk.class);
                } catch (Exception e) {
                    log.warn("条款判定失败，跳过: {} | {}", abbreviate(clause, 30), e.getMessage());
                    continue;
                }
                if (r == null) {
                    continue;
                }

                String level = r.risk() ? ("高".equals(r.severity()) ? "风险" : "注意") : "正常";
                keyClauses.add(Map.of(
                        "clause", clauseHead(clause),
                        "content", abbreviate(clause, 100),
                        "level", level));

                if (r.risk()) {
                    Map<String, Object> rp = new LinkedHashMap<>();
                    rp.put("point", r.riskDesc());
                    String severity = normalizeSeverity(r.severity());
                    rp.put("severity", severity);
                    rp.put("suggestion", r.suggestion() != null ? r.suggestion() : "");
                    rp.put("law", r.law() != null ? r.law() : "");          // ★ 法条依据（技术债#5 修复）
                    rp.put("source", r.source() != null ? r.source() : ""); // ★ 来源文档
                    riskPoints.add(rp);

                    if ("高".equals(severity)) hasHigh = true;
                    else if ("中".equals(severity)) hasMedium = true;
                }

                // 收集引用来源
                for (RagService.RetrievedChunk law : laws) {
                    if (seenSources.add(law.vectorId())) {
                        sources.add(Map.of(
                                "text", abbreviate(law.childText(), 300),
                                "score", law.score(),
                                "docTitle", law.docTitle(),
                                "docId", law.docId() != null ? law.docId() : 0));
                    }
                }
            }

            // ④ overallRisk 规则汇总 + LLM 生成 title/summary
            String overallRisk = hasHigh ? "高" : (hasMedium || !riskPoints.isEmpty() ? "中" : "低");
            ReviewSummary rs = generateSummary(contentText, riskPoints);
            String title = rs != null && rs.title() != null && !rs.title().isBlank()
                    ? rs.title() : firstLine(contentText);
            String summary = rs != null && rs.summary() != null ? rs.summary()
                    : buildFallbackSummary(riskPoints, overallRisk);

            // ⑤ 保存审查记录（不改表结构：risk_points 存含 law/source 的 JSON）
            ContractReview review = new ContractReview();
            review.setUserId(userId);
            review.setTitle(title);
            review.setContentText(contentText);
            review.setAiAnalysis(summary);
            review.setRiskPoints(JSON.toJSONString(riskPoints));
            review.setOverallRisk(overallRisk);
            review.setStatus("completed");
            contractReviewMapper.insert(review);
            log.info("合同审查完成，记录ID: {}, 条款数: {}, 风险数: {}, 总体: {}",
                    review.getId(), clauses.size(), riskPoints.size(), overallRisk);

            // ⑥ 返回（旧字段全保留 + 新增 sources）
            Map<String, Object> result = new HashMap<>();
            result.put("id", review.getId());
            result.put("title", title);
            result.put("keyClauses", keyClauses);
            result.put("riskPoints", riskPoints);
            result.put("overallRisk", overallRisk);
            result.put("summary", summary);
            result.put("sources", sources);          // ★ 新增：引用的法条来源（向后兼容）
            result.put("clauseCount", clauses.size());
            result.put("createdAt", review.getCreatedAt());
            return result;

        } catch (Exception e) {
            log.error("合同审查异常: {}", e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("message", "合同审查失败: " + e.getMessage());
            return errorResult;
        }
    }

    // ==================== 内部方法 ====================

    /** 检索到的法条拼进 Prompt（带编号和来源，供模型引用） */
    private String formatLaws(List<RagService.RetrievedChunk> laws) {
        if (laws.isEmpty()) {
            return "（法条库中未检索到直接相关法条。请基于公平原则分析，law 字段填\"未检索到直接相关法条\"。）";
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (RagService.RetrievedChunk c : laws) {
            sb.append("【法条").append(i++).append("】（来源：").append(c.docTitle()).append("）\n")
              .append(abbreviate(c.childText(), 500)).append("\n\n");
        }
        return sb.toString().trim();
    }

    /** title + summary 一次 LLM 生成（失败则用兜底） */
    private ReviewSummary generateSummary(String contentText, List<Map<String, Object>> riskPoints) {
        try {
            String prompt = String.format("""
                    请为这次租房合同审查生成标题和总体意见。
                    合同开头：%s
                    发现的风险点：%s
                    要求：title 不超过20字；summary 150字以内，先给总体结论，再提醒最需要注意的1-2个点。
                    """,
                    abbreviate(contentText, 200),
                    riskPoints.isEmpty() ? "无" : JSON.toJSONString(riskPoints));
            return chatClient.prompt()
                    .system("你是合同审查助手。")
                    .user(prompt)
                    .call()
                    .entity(ReviewSummary.class);
        } catch (Exception e) {
            log.warn("生成总结失败，使用兜底: {}", e.getMessage());
            return null;
        }
    }

    private String buildFallbackSummary(List<Map<String, Object>> riskPoints, String overallRisk) {
        if (riskPoints.isEmpty()) {
            return "本次审查未发现明显风险条款，整体风险等级：" + overallRisk + "。建议签约前仍仔细核对租金、押金、租期等关键信息。";
        }
        return "本次审查发现 " + riskPoints.size() + " 处风险条款，整体风险等级：" + overallRisk + "。请与房东协商修改后再签约。";
    }

    private String clauseHead(String clause) {
        int idx = clause.indexOf('条');
        return idx > 0 && idx < 12 ? clause.substring(0, idx + 1) : abbreviate(clause, 12);
    }

    private String firstLine(String text) {
        for (String line : text.split("\n")) {
            if (!line.isBlank()) {
                return abbreviate(line.trim(), 50);
            }
        }
        return "合同审查";
    }

    private String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /** severity 归一化：模型偶尔输出英文 high/medium/low */
    private String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) return "中";
        return switch (severity.trim().toLowerCase()) {
            case "high" -> "高";
            case "medium", "mid" -> "中";
            case "low" -> "低";
            default -> severity.trim();
        };
    }
}
