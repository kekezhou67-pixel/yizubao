package com.rental.platform.ai;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.rental.platform.entity.KbChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ES BM25 检索服务（AI 改造 · 阶段5）
 *
 * 课程要求的分工（讲义04 项目4 / 工程方案决策1）：
 *   ★ ES 检索在 Java 侧（企业级高并发、与业务同栈、RRF 融合就在本进程）
 *   Python 只做 AI 计算（向量化/FAISS/精排）
 *
 * BM25 的价值：抠字眼精确匹配（如"第七百一十三条"、"押金"），
 * 与向量检索（懂语义）互补 → 两路结果 RRF 融合。
 *
 * 索引 kb_chunks mapping：
 *   child_text: text（standard 分词 —— 没装 IK 插件时的稳妥选择，工程方案风险表）
 *   embedding:  dense_vector dims=512（预留 kNN，当前主检索走向 Python FAISS）
 */
@Service
public class EsSearchService {

    private static final Logger log = LoggerFactory.getLogger(EsSearchService.class);
    public static final String INDEX = "kb_chunks";

    private final ElasticsearchClient client;

    public EsSearchService(ElasticsearchClient client) {
        this.client = client;
    }

    /** 确保索引存在（不存在则按 mapping 创建）—— 服务启动或首次入库时调用 */
    public void ensureIndex() throws IOException {
        boolean exists = client.indices().exists(e -> e.index(INDEX)).value();
        if (exists) {
            return;
        }
        client.indices().create(c -> c
                .index(INDEX)
                .mappings(m -> m
                        // 注意：elasticsearch-java 的 long 类型 builder 方法名是 long_（Java 保留字）
                        .properties("chunk_id", p -> p.long_(l -> l))
                        .properties("doc_id", p -> p.long_(l -> l))
                        .properties("vector_id", p -> p.keyword(k -> k))
                        .properties("child_text", p -> p.text(t -> t.analyzer("standard")))
                        .properties("parent_text", p -> p.text(t -> t.index(false)))
                        .properties("embedding", p -> p.denseVector(d -> d.dims(512)))
                ));
        log.info("ES 索引 {} 已创建", INDEX);
    }

    /**
     * 批量写入分片（含向量）—— 讲义04 最佳实践：生产写 ES 用 bulk API
     */
    public int indexChunks(List<KbChunk> chunks, List<List<Double>> vectors) throws IOException {
        if (chunks.isEmpty()) {
            return 0;
        }
        ensureIndex();
        BulkRequest.Builder br = new BulkRequest.Builder();
        for (int i = 0; i < chunks.size(); i++) {
            KbChunk c = chunks.get(i);
            Map<String, Object> doc = new HashMap<>();
            doc.put("chunk_id", c.getId());
            doc.put("doc_id", c.getDocId());
            doc.put("vector_id", c.getVectorId());
            doc.put("child_text", c.getChildText());
            doc.put("parent_text", c.getParentText());
            if (vectors != null && i < vectors.size()) {
                doc.put("embedding", vectors.get(i));
            }
            br.operations(op -> op.index(idx -> idx
                    .index(INDEX)
                    .id(c.getVectorId())
                    .document(doc)));
        }
        BulkResponse resp = client.bulk(br.build());
        if (resp.errors()) {
            log.error("ES bulk 写入存在失败项: {}", resp.items().stream()
                    .filter(it -> it.error() != null)
                    .map(it -> it.error().reason())
                    .toList());
        }
        // 立即可搜（写入后刷新 —— 讲义04：es.indices.refresh()）
        client.indices().refresh(r -> r.index(INDEX));
        return chunks.size();
    }

    /**
     * BM25 关键词检索
     * @return vector_id 列表（按相关度降序）
     */
    public List<String> bm25Search(String query, int topK) {
        try {
            SearchResponse<Map> resp = client.search(s -> s
                            .index(INDEX)
                            .query(q -> q.match(m -> m
                                    .field("child_text")
                                    .query(query)))
                            .size(topK),
                    Map.class);
            List<String> ids = new ArrayList<>();
            for (Hit<Map> hit : resp.hits().hits()) {
                Map<?, ?> src = hit.source();
                if (src != null && src.get("vector_id") != null) {
                    ids.add(String.valueOf(src.get("vector_id")));
                }
            }
            return ids;
        } catch (Exception e) {
            // ES 挂了不能拖垮整个 RAG —— 返回空，让向量单路兜底
            log.error("ES BM25 检索失败（降级为向量单路）: {}", e.getMessage());
            return List.of();
        }
    }

    /** 按文档删除 ES 数据 */
    public void deleteByDoc(long docId) {
        try {
            client.deleteByQuery(d -> d
                    .index(INDEX)
                    .query(q -> q.term(t -> t.field("doc_id").value(docId)))
                    .refresh(true));
        } catch (Exception e) {
            log.warn("ES 删除文档 {} 失败: {}", docId, e.getMessage());
        }
    }

    /** 连通性检查（部署验收用） */
    public boolean ping() {
        try {
            return client.ping().value();
        } catch (Exception e) {
            return false;
        }
    }
}
