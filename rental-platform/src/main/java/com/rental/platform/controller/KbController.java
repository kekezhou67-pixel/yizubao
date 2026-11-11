package com.rental.platform.controller;

import com.rental.platform.ai.EsSearchService;
import com.rental.platform.ai.RagService;
import com.rental.platform.ai.RrfService;
import com.rental.platform.client.PythonAiClient;
import com.rental.platform.config.UserContextHolder;
import com.rental.platform.entity.KbDocument;
import com.rental.platform.service.KbDocumentService;
import com.rental.platform.utils.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理接口（AI 改造 · 阶段5，工程方案 §5.2）
 *
 * - POST   /api/kb/documents         文本入库（管理员）
 * - POST   /api/kb/documents/upload  文件入库：PDF/Word/Excel/TXT（管理员，经 Python /parse）
 * - GET    /api/kb/documents         文档列表
 * - DELETE /api/kb/documents/{id}    删除（级联 MySQL 分片 + FAISS + ES）
 * - POST   /api/kb/search            检索调试：返回 FAISS/BM25/RRF 三路明细
 */
@RestController
@RequestMapping("/api/kb")
public class KbController {

    private static final Logger log = LoggerFactory.getLogger(KbController.class);

    private final KbDocumentService kbDocumentService;
    private final PythonAiClient pythonAiClient;
    private final EsSearchService esSearchService;
    private final RrfService rrfService;
    private final RagService ragService;

    public KbController(KbDocumentService kbDocumentService,
                        PythonAiClient pythonAiClient,
                        EsSearchService esSearchService,
                        RrfService rrfService,
                        RagService ragService) {
        this.kbDocumentService = kbDocumentService;
        this.pythonAiClient = pythonAiClient;
        this.esSearchService = esSearchService;
        this.rrfService = rrfService;
        this.ragService = ragService;
    }

    /** 文本入库 */
    @PostMapping("/documents")
    public Result<Map<String, Object>> create(@RequestBody Map<String, String> body) {
        if (!UserContextHolder.hasRole("ADMIN")) {
            return Result.error(403, "仅管理员可维护知识库");
        }
        String title = body.get("title");
        String content = body.get("content");
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            return Result.error("title 和 content 不能为空");
        }
        try {
            KbDocument doc = kbDocumentService.createFromText(
                    title.trim(),
                    body.getOrDefault("sourceType", "GUIDE"),
                    body.get("city"),
                    content,
                    UserContextHolder.getCurrentUserId());
            return Result.success(Map.of(
                    "id", doc.getId(),
                    "title", doc.getTitle(),
                    "chunkCount", doc.getChunkCount(),
                    "message", "入库成功"));
        } catch (Exception e) {
            log.error("知识库入库失败: {}", e.getMessage(), e);
            return Result.error("入库失败: " + e.getMessage());
        }
    }

    /** 文件入库（multipart：file + title/sourceType/city 表单字段） */
    @PostMapping("/documents/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(required = false) String title,
                                              @RequestParam(required = false, defaultValue = "GUIDE") String sourceType,
                                              @RequestParam(required = false) String city) {
        if (!UserContextHolder.hasRole("ADMIN")) {
            return Result.error(403, "仅管理员可维护知识库");
        }
        if (file.isEmpty()) {
            return Result.error("文件为空");
        }
        try {
            // Python /parse：PDF/Word/Excel/TXT → 纯文本
            Map<String, Object> parsed = pythonAiClient.parse(
                    file.getOriginalFilename(), file.getBytes());
            String content = (String) parsed.get("text");
            if (content == null || content.isBlank()) {
                return Result.error("文件解析结果为空（扫描件请走 OCR 通道）");
            }
            String docTitle = title != null && !title.isBlank()
                    ? title.trim() : file.getOriginalFilename();
            KbDocument doc = kbDocumentService.createFromText(
                    docTitle, sourceType, city, content,
                    UserContextHolder.getCurrentUserId());
            return Result.success(Map.of(
                    "id", doc.getId(),
                    "title", doc.getTitle(),
                    "chunkCount", doc.getChunkCount(),
                    "charCount", content.length(),
                    "message", "文件解析并入库成功"));
        } catch (Exception e) {
            log.error("文件入库失败: {}", e.getMessage(), e);
            return Result.error("文件入库失败: " + e.getMessage());
        }
    }

    /** 文档列表（不含 content 大字段） */
    @GetMapping("/documents")
    public Result<List<KbDocument>> list(@RequestParam(required = false) String sourceType,
                                         @RequestParam(required = false) String city) {
        return Result.success(kbDocumentService.list(sourceType, city));
    }

    /** 删除文档（级联清理） */
    @DeleteMapping("/documents/{id}")
    public Result<String> delete(@PathVariable Long id) {
        if (!UserContextHolder.hasRole("ADMIN")) {
            return Result.error(403, "仅管理员可维护知识库");
        }
        try {
            kbDocumentService.delete(id);
            return Result.success("文档已删除（含分片/向量/ES数据）");
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 检索调试接口 —— 展示双路召回 + RRF 明细（答辩演示混合检索价值的利器）
     */
    @PostMapping("/search")
    public Result<Map<String, Object>> searchDebug(@RequestBody Map<String, Object> body) {
        String query = String.valueOf(body.getOrDefault("query", ""));
        int topK = body.get("topK") != null ? Integer.parseInt(body.get("topK").toString()) : 10;
        if (query.isBlank()) {
            return Result.error("query 不能为空");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        // 向量路
        try {
            detail.put("faiss", pythonAiClient.search(query, topK));
        } catch (Exception e) {
            detail.put("faiss", "不可用: " + e.getMessage());
        }
        // BM25 路
        List<String> bm25 = esSearchService.bm25Search(query, topK);
        detail.put("bm25", bm25);
        // RRF
        if (detail.get("faiss") instanceof List<?> faissHits) {
            List<String> vectorIds = faissHits.stream()
                    .filter(h -> h instanceof PythonAiClient.SearchHit)
                    .map(h -> ((PythonAiClient.SearchHit) h).vectorId())
                    .toList();
            RrfService.FuseResult fused = rrfService.fuse(vectorIds, bm25, topK);
            Map<String, Object> rrf = new LinkedHashMap<>();
            rrf.put("ids", fused.ids());
            rrf.put("scores", fused.scores());
            rrf.put("detail", fused.detail());
            detail.put("rrf", rrf);
        }
        // 最终精排结果（完整 RAG 检索链）
        List<RagService.RetrievedChunk> finalChunks = ragService.retrieve(query, 5);
        detail.put("final", finalChunks.stream().map(c -> Map.of(
                "vectorId", c.vectorId(),
                "docTitle", c.docTitle(),
                "score", c.score(),
                "from", c.from(),
                "text", c.childText().length() > 100 ? c.childText().substring(0, 100) + "..." : c.childText()
        )).toList());
        return Result.success(detail);
    }
}
