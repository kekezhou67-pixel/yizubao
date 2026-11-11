package com.rental.platform.ai;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF（Reciprocal Rank Fusion）融合服务（AI 改造 · 阶段5）
 *
 * 讲义04 §6：score(doc) = Σ 1/(k + rank)
 * - k 取 60（论文经验值：平滑排名差、防第一名权重过大）
 * - 只比排名不比原始分 —— 向量余弦分和 BM25 分不可比，排名可比
 * - 双路都靠前的文档融合分最高
 *
 * 为什么放在 Java（工程方案决策1）：
 * 向量路（Python FAISS）和 BM25 路（Java ES）的结果在 Java 汇合，
 * 同进程融合零跨进程开销，且检索参数来自业务侧（权限/城市过滤）。
 */
@Service
public class RrfService {

    /** RRF 平滑常数（讲义04：k=60） */
    private static final int RRF_K = 60;

    /**
     * 融合两路检索结果
     * @param vectorIds 向量路命中的 vector_id（按分数降序）
     * @param bm25Ids   BM25 路命中的 vector_id（按分数降序）
     * @param limit     融合后保留条数
     * @return 融合排序后的 vector_id 列表，附带每路的贡献明细
     */
    public FuseResult fuse(List<String> vectorIds, List<String> bm25Ids, int limit) {
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, String> detail = new LinkedHashMap<>();

        for (int i = 0; i < vectorIds.size(); i++) {
            String id = vectorIds.get(i);
            scores.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
            detail.merge(id, "vec#" + (i + 1), (a, b) -> a + "+" + b);
        }
        for (int i = 0; i < bm25Ids.size(); i++) {
            String id = bm25Ids.get(i);
            scores.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
            detail.merge(id, "bm25#" + (i + 1), (a, b) -> a + "+" + b);
        }

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Map.Entry.<String, Double>comparingByValue().reversed());

        List<String> fusedIds = new ArrayList<>();
        Map<String, Double> fusedScores = new LinkedHashMap<>();
        Map<String, String> fusedDetail = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : sorted.subList(0, Math.min(limit, sorted.size()))) {
            fusedIds.add(e.getKey());
            fusedScores.put(e.getKey(), e.getValue());
            fusedDetail.put(e.getKey(), detail.get(e.getKey()));
        }
        return new FuseResult(fusedIds, fusedScores, fusedDetail);
    }

    /** 融合结果：id 列表 + 分数 + 来源明细（调试接口用） */
    public record FuseResult(List<String> ids,
                             Map<String, Double> scores,
                             Map<String, String> detail) {}
}
