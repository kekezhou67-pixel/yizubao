package com.rental.platform.service;

import com.rental.platform.client.PythonAiClient;
import com.rental.platform.entity.KbChunk;
import com.rental.platform.entity.KbDocument;
import com.rental.platform.mapper.KbChunkMapper;
import com.rental.platform.mapper.KbDocumentMapper;
import com.rental.platform.ai.EsSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库文档服务（AI 改造 · 阶段5）—— 入库/删除编排
 *
 * 入库流程（工程方案 §5.1）：
 *   ① 保存 kb_document（MySQL）
 *   ② 调 Python /split → 父子分片
 *   ③ 生成 vector_id（"kb-{docId}-{childIndex}"），batchInsert kb_chunk
 *   ④ 调 Python /index → 子块向量化 + 写 FAISS
 *   ⑤ 调 Python /embed → 拿向量，Java 写 ES（bulk，含 embedding 字段）
 *   ⑥ updateChunkCount
 *
 * 一致性策略：MySQL 事务包裹；FAISS/ES 写失败抛异常回滚 MySQL，
 * 残留向量靠 deleteByDoc 清理或重新入库覆盖（课程项目够用）。
 */
@Service
public class KbDocumentService {

    private static final Logger log = LoggerFactory.getLogger(KbDocumentService.class);

    private final KbDocumentMapper kbDocumentMapper;
    private final KbChunkMapper kbChunkMapper;
    private final PythonAiClient pythonAiClient;
    private final EsSearchService esSearchService;

    public KbDocumentService(KbDocumentMapper kbDocumentMapper,
                             KbChunkMapper kbChunkMapper,
                             PythonAiClient pythonAiClient,
                             EsSearchService esSearchService) {
        this.kbDocumentMapper = kbDocumentMapper;
        this.kbChunkMapper = kbChunkMapper;
        this.pythonAiClient = pythonAiClient;
        this.esSearchService = esSearchService;
    }

    /**
     * 从纯文本创建知识库文档并完成全链路入库
     */
    @Transactional(rollbackFor = Exception.class)
    public KbDocument createFromText(String title, String sourceType, String city,
                                     String content, Long userId) {
        // ① 保存文档
        KbDocument doc = new KbDocument();
        doc.setUserId(userId);
        doc.setTitle(title);
        doc.setSourceType(sourceType != null ? sourceType : "GUIDE");
        doc.setCity(city);
        doc.setContent(content);
        doc.setChunkCount(0);
        kbDocumentMapper.insert(doc);
        Long docId = doc.getId();

        // ② Python 父子分片
        List<PythonAiClient.ChunkDto> pieces = pythonAiClient.split(content);
        if (pieces.isEmpty()) {
            throw new IllegalStateException("分片结果为空，文档可能没有有效内容");
        }
        log.info("文档[{}]《{}》分片完成: {} 个子块", docId, title, pieces.size());

        // ③ 保存分片（vector_id 规则：kb-{docId}-{childIndex}）
        List<KbChunk> chunks = new ArrayList<>();
        for (PythonAiClient.ChunkDto p : pieces) {
            KbChunk c = new KbChunk();
            c.setDocId(docId);
            c.setChunkIndex(p.childIndex());
            c.setParentText(p.parentText());
            c.setChildText(p.childText());
            c.setVectorId("kb-" + docId + "-" + p.childIndex());
            c.setTokenCount(p.childText().length());   // 近似：中文 1字≈1token（讲义02：1汉字≈2token，此处仅做统计参考）
            chunks.add(c);
        }
        kbChunkMapper.batchInsert(chunks);

        // ④ 写 FAISS（Python 内部会向量化 child_text）
        List<Map<String, Object>> indexBody = new ArrayList<>();
        for (KbChunk c : chunks) {
            Map<String, Object> m = new HashMap<>();
            m.put("vector_id", c.getVectorId());
            m.put("child_text", c.getChildText());
            m.put("doc_id", docId);
            m.put("chunk_index", c.getChunkIndex());
            indexBody.add(m);
        }
        pythonAiClient.index(indexBody);

        // ⑤ 写 ES（bulk + embedding 向量）
        try {
            List<List<Double>> vectors = pythonAiClient.embed(
                    chunks.stream().map(KbChunk::getChildText).toList());
            esSearchService.indexChunks(chunks, vectors);
        } catch (Exception e) {
            // ES 挂了不阻塞入库主流程（BM25 路缺失，向量路仍可用），但记录错误
            log.error("文档[{}] 写入 ES 失败（向量检索仍可用）: {}", docId, e.getMessage());
        }

        // ⑥ 更新分片数
        kbDocumentMapper.updateChunkCount(docId, chunks.size());
        doc.setChunkCount(chunks.size());
        log.info("文档[{}]《{}》入库完成: {} 分片", docId, title, chunks.size());
        return doc;
    }

    /**
     * 删除文档（级联清理 MySQL 分片 + FAISS 向量 + ES 数据）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(long docId) {
        KbDocument doc = kbDocumentMapper.findById(docId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在: " + docId);
        }
        // MySQL：逻辑删文档 + 物理删分片
        kbDocumentMapper.softDelete(docId);
        kbChunkMapper.deleteByDocId(docId);
        // FAISS：按 doc_id 重建索引删除
        try {
            int removed = pythonAiClient.deleteDocVectors(docId);
            log.info("FAISS 清理文档[{}]向量 {} 条", docId, removed);
        } catch (Exception e) {
            log.error("FAISS 清理失败（不影响 MySQL 删除）: {}", e.getMessage());
        }
        // ES
        esSearchService.deleteByDoc(docId);
    }

    public List<KbDocument> list(String sourceType, String city) {
        return kbDocumentMapper.list(sourceType, city);
    }
}
