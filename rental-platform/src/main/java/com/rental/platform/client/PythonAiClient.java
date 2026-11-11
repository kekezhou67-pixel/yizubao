package com.rental.platform.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Python AI 服务客户端（AI 改造 · 阶段4）
 *
 * 双栈分工（工程方案决策4 / 讲义04 项目4）：
 *   Python 只做 AI 计算（解析/分片/向量化/向量检索/精排），
 *   Java 管业务、调度、ES 检索、RRF 融合、最终生成。
 *
 * 约定（工程方案 §4.5 的坑）：请求体字段用下划线命名（top_k / child_text），
 * Python 端 Pydantic 模型按此定义。
 *
 * 超时设计：模型推理（尤其 rerank 批量）可能较慢，统一 60s；
 * parse 大文档 120s。超时后抛 IllegalStateException，由上层业务决定降级。
 */
@Service
public class PythonAiClient {

    private static final Logger log = LoggerFactory.getLogger(PythonAiClient.class);

    private final WebClient webClient;
    private final ObjectMapper mapper;

    public PythonAiClient(
            @Value("${ai.python.base-url:http://localhost:8000}") String baseUrl,
            ObjectMapper mapper) {
        this.mapper = mapper;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                // 文档解析结果可能很大，放宽内存缓冲
                .codecs(c -> c.defaultCodecs().maxInMemorySize(64 * 1024 * 1024))
                .build();
        log.info("PythonAiClient 初始化，目标服务: {}", baseUrl);
    }

    // ==================== DTO ====================

    /** 分片结果条目 */
    public record ChunkDto(int parentIndex, String parentText, int childIndex, String childText) {}

    /** 向量检索命中 */
    public record SearchHit(String vectorId, double score, Integer docId, Integer chunkIndex) {}

    /** 精排候选 */
    public record RerankCandidate(String vectorId, String text) {}

    /** 精排结果 */
    public record RerankHit(String vectorId, String text, double score) {}

    // ==================== 接口封装 ====================

    /** 服务健康检查（部署验证用） */
    public Map<String, Object> health() {
        return get("/health", new ParameterizedTypeReference<>() {}, Duration.ofSeconds(10));
    }

    /**
     * 文档解析：PDF/Word/Excel/TXT → 纯文本
     * @return {text, meta:{filename,type,char_count}}
     */
    public Map<String, Object> parse(String filename, byte[] fileBytes) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new org.springframework.core.io.ByteArrayResource(fileBytes) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                })
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        return webClient.post()
                .uri("/parse")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(body.build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block(Duration.ofSeconds(120));
    }

    /**
     * 图片 OCR（阶段7A）：合同照片/扫描件 → 文字
     * PaddleOCR 首次调用要下载模型，超时放宽到 5 分钟
     * @return OCR 出的纯文本（按行拼接）
     */
    public String ocr(String filename, byte[] imageBytes) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new org.springframework.core.io.ByteArrayResource(imageBytes) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                })
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        try {
            Map<String, Object> resp = webClient.post()
                    .uri("/ocr")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(body.build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(Duration.ofMinutes(5));
            return resp != null && resp.get("text") != null ? String.valueOf(resp.get("text")) : "";
        } catch (WebClientResponseException e) {
            throw new IllegalStateException("OCR 服务返回 " + e.getStatusCode()
                    + ": " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new IllegalStateException("OCR 服务不可用: " + e.getMessage(), e);
        }
    }

    /** 父子分片（Java 拿结果存 kb_chunk） */
    public List<ChunkDto> split(String text) {
        Map<String, Object> resp = post("/split",
                Map.of("text", text),
                new ParameterizedTypeReference<Map<String, Object>>() {}, Duration.ofSeconds(60));
        return castMapList(resp.get("chunks")).stream()
                .map(m -> new ChunkDto(
                        toInt(m.get("parent_index")),
                        (String) m.get("parent_text"),
                        toInt(m.get("child_index")),
                        (String) m.get("child_text")))
                .toList();
    }

    /** 文本向量化（query 与文档必须同模型 —— query 向量也从 Python 走） */
    public List<List<Double>> embed(List<String> texts) {
        Map<String, Object> resp = post("/embed",
                Map.of("texts", texts),
                new ParameterizedTypeReference<Map<String, Object>>() {}, Duration.ofSeconds(60));
        return castList(resp.get("vectors")).stream()
                .map(v -> castList(v).stream()
                        .map(x -> ((Number) x).doubleValue())
                        .toList())
                .toList();
    }

    /** 子块向量化 + 写入 FAISS（入库流程调用） */
    public int index(List<Map<String, Object>> chunks) {
        Map<String, Object> resp = post("/index",
                Map.of("chunks", chunks),
                new ParameterizedTypeReference<>() {}, Duration.ofMinutes(5));
        return toInt(resp.get("indexed"));
    }

    /** FAISS 向量检索（返回 vector_id + score，Java 拿去和 ES 结果做 RRF） */
    public List<SearchHit> search(String query, int topK) {
        Map<String, Object> resp = post("/search",
                Map.of("query", query, "top_k", topK),
                new ParameterizedTypeReference<Map<String, Object>>() {}, Duration.ofSeconds(60));
        return castMapList(resp.get("results")).stream()
                .map(m -> new SearchHit(
                        (String) m.get("vector_id"),
                        toDouble(m.get("score")),
                        m.get("doc_id") != null ? toInt(m.get("doc_id")) : null,
                        m.get("chunk_index") != null ? toInt(m.get("chunk_index")) : null))
                .toList();
    }

    /** CrossEncoder 精排（RRF 融合后 top-10 → top-5） */
    public List<RerankHit> rerank(String query, List<RerankCandidate> candidates, int topK) {
        List<Map<String, String>> raw = candidates.stream()
                .map(c -> Map.of("vector_id", c.vectorId(), "text", c.text()))
                .toList();
        Map<String, Object> resp = post("/rerank",
                Map.of("query", query, "candidates", raw, "top_k", topK),
                new ParameterizedTypeReference<Map<String, Object>>() {}, Duration.ofSeconds(120));
        return castMapList(resp.get("results")).stream()
                .map(m -> new RerankHit(
                        (String) m.get("vector_id"),
                        (String) m.get("text"),
                        toDouble(m.get("score"))))
                .toList();
    }

    /** 按文档删除向量（删除知识库文档时级联） */
    public int deleteDocVectors(long docId) {
        Map<String, Object> resp = post("/index/delete-doc",
                Map.of("doc_id", docId),
                new ParameterizedTypeReference<>() {}, Duration.ofSeconds(60));
        return toInt(resp.get("removed"));
    }

    // ==================== CLIP 图文跨模态（阶段7B） ====================

    /** CLIP 检索命中（img 索引 meta：vector_id/image_url/caption + score） */
    public record ClipHit(String vectorId, double score, String imageUrl, String caption) {}

    /**
     * 房源图片入库：CLIP 向量化 → 写 img 索引
     * @return vector_id
     */
    public String clipIndex(String filename, byte[] imageBytes, String imageUrl, String caption) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new org.springframework.core.io.ByteArrayResource(imageBytes) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                })
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        body.part("image_url", imageUrl);
        body.part("caption", caption != null ? caption : "");

        Map<String, Object> resp = postMultipart("/clip/index", body, Duration.ofMinutes(2));
        return resp != null && resp.get("vector_id") != null ? String.valueOf(resp.get("vector_id")) : null;
    }

    /** 图搜图：上传图片 → 相似房源图 */
    public List<ClipHit> clipSearchImage(String filename, byte[] imageBytes, int topK) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new org.springframework.core.io.ByteArrayResource(imageBytes) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                })
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        body.part("top_k", String.valueOf(topK));

        Map<String, Object> resp = postMultipart("/clip/search-image", body, Duration.ofMinutes(2));
        return toClipHits(resp);
    }

    /** 文搜图：文字描述 → 匹配 caption/图像（跨模态） */
    public List<ClipHit> clipSearchText(String text, int topK) {
        Map<String, Object> resp = post("/clip/search-text",
                Map.of("text", text, "top_k", topK),
                new ParameterizedTypeReference<Map<String, Object>>() {}, Duration.ofMinutes(2));
        return toClipHits(resp);
    }

    private List<ClipHit> toClipHits(Map<String, Object> resp) {
        if (resp == null) return List.of();
        return castMapList(resp.get("results")).stream()
                .map(m -> new ClipHit(
                        String.valueOf(m.get("vector_id")),
                        toDouble(m.get("score")),
                        m.get("image_url") != null ? String.valueOf(m.get("image_url")) : "",
                        m.get("caption") != null ? String.valueOf(m.get("caption")) : ""))
                .toList();
    }

    private Map<String, Object> postMultipart(String uri, MultipartBodyBuilder body, Duration timeout) {
        try {
            return webClient.post().uri(uri)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(body.build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(timeout);
        } catch (WebClientResponseException e) {
            throw new IllegalStateException("Python AI 服务 " + uri + " 返回 " + e.getStatusCode()
                    + ": " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Python AI 服务不可用(" + uri + "): " + e.getMessage(), e);
        }
    }

    // ==================== 内部工具 ====================

    private <T> T get(String uri, ParameterizedTypeReference<T> type, Duration timeout) {
        try {
            return webClient.get().uri(uri)
                    .retrieve()
                    .bodyToMono(type)
                    .block(timeout);
        } catch (WebClientResponseException e) {
            throw new IllegalStateException("Python AI 服务返回 " + e.getStatusCode()
                    + ": " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Python AI 服务不可用: " + e.getMessage(), e);
        }
    }

    private <T> T post(String uri, Object body, ParameterizedTypeReference<T> type, Duration timeout) {
        try {
            return webClient.post().uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(type)
                    .block(timeout);
        } catch (WebClientResponseException e) {
            throw new IllegalStateException("Python AI 服务 " + uri + " 返回 " + e.getStatusCode()
                    + ": " + e.getResponseBodyAsString(), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Python AI 服务不可用(" + uri + "): " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(Object obj) {
        return obj instanceof List ? (List<Object>) obj : List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castMapList(Object obj) {
        if (!(obj instanceof List)) {
            return List.of();
        }
        return ((List<?>) obj).stream()
                .filter(e -> e instanceof Map)
                .map(e -> (Map<String, Object>) e)
                .toList();
    }

    private static int toInt(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static double toDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }
}
