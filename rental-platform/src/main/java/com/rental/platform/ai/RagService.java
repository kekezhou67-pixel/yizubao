package com.rental.platform.ai;

import com.rental.platform.client.PythonAiClient;
import com.rental.platform.entity.KbChunk;
import com.rental.platform.entity.KbDocument;
import com.rental.platform.mapper.KbChunkMapper;
import com.rental.platform.mapper.KbDocumentMapper;
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
 * RAG 编排服务（AI 改造 · 阶段5）—— 一次问答的完整调用链（工程方案 §3.3）：
 *
 *   ① Python /search  → FAISS 语义检索 Top-20（向量路）
 *   ② Java  ES BM25   → 关键词检索 Top-20（词面路，课程要求 ES 在 Java 侧）
 *   ③ Java  RRF 融合  → Top-10（k=60，只比排名不比原始分）
 *   ④ MySQL 反查分片  → child 检索粒度 / parent 生成粒度
 *   ⑤ Python /rerank  → CrossEncoder 精排 Top-5
 *   ⑥ 拼 RAG Prompt   → parent 去重 + 编号 + 文档标题
 *   ⑦ ChatClient 生成 → 带【资料N】引用
 *
 * 降级策略（任何一路挂了都不能让问答整体失败）：
 * - FAISS 挂 → ES BM25 单路
 * - ES 挂    → 向量单路（EsSearchService 内部已兜底返回空）
 * - rerank 挂 → 直接用 RRF Top-5
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    /** 各路召回条数 */
    private static final int RECALL_TOP_K = 20;
    /** RRF 融合后条数 */
    private static final int FUSE_TOP_K = 10;
    /** 精排后最终条数 */
    private static final int FINAL_TOP_K = 5;

    private final PythonAiClient pythonAiClient;
    private final EsSearchService esSearchService;
    private final RrfService rrfService;
    private final KbChunkMapper kbChunkMapper;
    private final KbDocumentMapper kbDocumentMapper;
    private final ChatClient chatClient;

    public RagService(PythonAiClient pythonAiClient,
                      EsSearchService esSearchService,
                      RrfService rrfService,
                      KbChunkMapper kbChunkMapper,
                      KbDocumentMapper kbDocumentMapper,
                      @Lazy ChatClient chatClient) {   // @Lazy 打破循环依赖（MCP toolResolver 链）
        this.pythonAiClient = pythonAiClient;
        this.esSearchService = esSearchService;
        this.rrfService = rrfService;
        this.kbChunkMapper = kbChunkMapper;
        this.kbDocumentMapper = kbDocumentMapper;
        this.chatClient = chatClient;
    }

    // ==================== DTO ====================

    /** 检索命中的分片（含父块与来源） */
    public record RetrievedChunk(String vectorId, Long chunkId, Long docId,
                                 String docTitle, String parentText, String childText,
                                 double score, String from) {}

    /** 问答结果 */
    public record QaResult(String answer, List<SourceItem> sources) {}

    /** 引用来源（前端展示） */
    public record SourceItem(String text, double score, String docTitle, Long docId) {}

    // ==================== 检索 ====================

    /**
     * 混合检索：双路召回 → RRF → 精排
     */
    public List<RetrievedChunk> retrieve(String query) {
        return retrieve(query, FINAL_TOP_K);
    }

    public List<RetrievedChunk> retrieve(String query, int finalTopK) {
        // ① 向量路（Python FAISS）
        List<String> vectorIds;
        Map<String, Double> vectorScores = new HashMap<>();
        try {
            List<PythonAiClient.SearchHit> hits = pythonAiClient.search(query, RECALL_TOP_K);
            vectorIds = hits.stream().map(PythonAiClient.SearchHit::vectorId).toList();
            hits.forEach(h -> vectorScores.put(h.vectorId(), h.score()));
        } catch (Exception e) {
            log.error("FAISS 向量检索失败，降级 BM25 单路: {}", e.getMessage());
            vectorIds = List.of();
        }

        // ② BM25 路（Java ES，内部已兜底）
        List<String> bm25Ids = esSearchService.bm25Search(query, RECALL_TOP_K);

        if (vectorIds.isEmpty() && bm25Ids.isEmpty()) {
            return List.of();
        }

        // ③ RRF 融合
        RrfService.FuseResult fused = rrfService.fuse(vectorIds, bm25Ids, FUSE_TOP_K);

        // ④ MySQL 反查分片内容（按融合顺序重排）
        List<KbChunk> chunks = kbChunkMapper.findByVectorIds(fused.ids());
        Map<String, KbChunk> byVectorId = new LinkedHashMap<>();
        for (KbChunk c : chunks) {
            byVectorId.put(c.getVectorId(), c);
        }
        List<KbChunk> ordered = new ArrayList<>();
        for (String vid : fused.ids()) {
            KbChunk c = byVectorId.get(vid);
            if (c != null) {
                ordered.add(c);
            }
        }
        if (ordered.isEmpty()) {
            return List.of();
        }

        // 文档标题批量查（docId → title）
        Map<Long, String> docTitles = loadDocTitles(ordered);

        // ⑤ CrossEncoder 精排（挂了就用 RRF 顺序）
        List<KbChunk> finalChunks;
        Map<String, Double> rerankScores = new HashMap<>();
        try {
            List<PythonAiClient.RerankCandidate> candidates = ordered.stream()
                    .map(c -> new PythonAiClient.RerankCandidate(c.getVectorId(), c.getChildText()))
                    .toList();
            List<PythonAiClient.RerankHit> reranked =
                    pythonAiClient.rerank(query, candidates, finalTopK);
            Map<String, KbChunk> pick = new LinkedHashMap<>();
            for (PythonAiClient.RerankHit h : reranked) {
                rerankScores.put(h.vectorId(), h.score());
                KbChunk c = byVectorId.get(h.vectorId());
                if (c != null) {
                    pick.put(h.vectorId(), c);
                }
            }
            finalChunks = new ArrayList<>(pick.values());
        } catch (Exception e) {
            log.warn("Rerank 失败，直接用 RRF Top-{}: {}", finalTopK, e.getMessage());
            finalChunks = ordered.subList(0, Math.min(finalTopK, ordered.size()));
        }

        // 组装结果
        List<RetrievedChunk> results = new ArrayList<>();
        for (KbChunk c : finalChunks) {
            double score = rerankScores.getOrDefault(c.getVectorId(),
                    fused.scores().getOrDefault(c.getVectorId(), 0.0));
            String from = fused.detail().getOrDefault(c.getVectorId(), "-");
            results.add(new RetrievedChunk(
                    c.getVectorId(), c.getId(), c.getDocId(),
                    docTitles.getOrDefault(c.getDocId(), "未知文档"),
                    c.getParentText(), c.getChildText(), score, from));
        }
        return results;
    }

    // ==================== 问答 ====================

    /**
     * RAG 政策问答（带引用）
     */
    public QaResult answer(String question) {
        List<RetrievedChunk> chunks = retrieve(question);

        if (chunks.isEmpty()) {
            return new QaResult("知识库中暂时没有相关资料，无法回答这个问题。建议咨询当地住建部门或专业律师。",
                    List.of());
        }

        // ⑥ 拼 context：parent 去重（多个 child 可能同属一个 parent），编号【资料N】
        Map<String, Integer> parentOrder = new LinkedHashMap<>();   // parentText → 编号
        Map<Integer, String> parentTitles = new HashMap<>();
        StringBuilder context = new StringBuilder();
        int no = 1;
        for (RetrievedChunk c : chunks) {
            String parent = c.parentText() != null && !c.parentText().isBlank()
                    ? c.parentText() : c.childText();
            if (!parentOrder.containsKey(parent)) {
                parentOrder.put(parent, no);
                parentTitles.put(no, c.docTitle());
                context.append("【资料").append(no).append("】（来源：")
                        .append(c.docTitle()).append("）\n")
                        .append(parent).append("\n\n");
                no++;
            }
        }

        // ⑦ 生成
        String prompt = PromptTemplates.RAG_QA
                .replace("{context}", context.toString().trim())
                .replace("{question}", question);
        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        // sources：每个 parent 一条（截取前300字给前端展示）
        List<SourceItem> sources = new ArrayList<>();
        for (Map.Entry<String, Integer> e : parentOrder.entrySet()) {
            String text = e.getKey();
            sources.add(new SourceItem(
                    text.length() > 300 ? text.substring(0, 300) + "..." : text,
                    chunks.stream()
                            .filter(c -> e.getKey().equals(
                                    c.parentText() != null && !c.parentText().isBlank()
                                            ? c.parentText() : c.childText()))
                            .mapToDouble(RetrievedChunk::score).max().orElse(0),
                    parentTitles.get(e.getValue()),
                    chunks.stream()
                            .filter(c -> e.getKey().equals(
                                    c.parentText() != null && !c.parentText().isBlank()
                                            ? c.parentText() : c.childText()))
                            .map(RetrievedChunk::docId).findFirst().orElse(null)));
        }
        return new QaResult(answer, sources);
    }

    // ==================== 内部 ====================

    private Map<Long, String> loadDocTitles(List<KbChunk> chunks) {
        Map<Long, String> titles = new HashMap<>();
        LinkedHashSet<Long> docIds = new LinkedHashSet<>();
        chunks.forEach(c -> docIds.add(c.getDocId()));
        for (Long docId : docIds) {
            try {
                KbDocument doc = kbDocumentMapper.findById(docId);
                if (doc != null) {
                    titles.put(docId, doc.getTitle());
                }
            } catch (Exception ignored) { /* 标题查不到不致命 */ }
        }
        return titles;
    }
}
