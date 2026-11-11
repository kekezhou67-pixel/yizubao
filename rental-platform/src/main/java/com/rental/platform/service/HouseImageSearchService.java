package com.rental.platform.service;

import com.rental.platform.client.PythonAiClient;
import com.rental.platform.dto.HouseVO;
import com.rental.platform.entity.HouseImage;
import com.rental.platform.mapper.HouseImageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 以图搜房服务（AI 改造 · 阶段7B）
 *
 * 两种检索（讲义05 跨模态四形式中落地两条）：
 * - 图→图：用户上传照片 → CLIP 编码 → img 索引相似检索 → 反查房源
 * - 文→图：文字描述（"落地窗原木风"）→ CLIP 文本编码 → 同一空间检索 caption/图像
 *
 * 入库（reindex）：
 * 扫房源 cover_image → 读前端 public 目录物理文件 → Python /clip/index（caption=房源标题）
 * → 每个房源写 house_image（vector_id 关联）
 * 同一张图只编码一次（imageUrl→vectorId 缓存），多个房源共享 vector_id。
 */
@Service
public class HouseImageSearchService {

    private static final Logger log = LoggerFactory.getLogger(HouseImageSearchService.class);

    private final PythonAiClient pythonAiClient;
    private final HouseImageMapper houseImageMapper;
    private final HouseService houseService;

    /** 房源图片物理根目录（cover_image 是 /house-images/xx.jpg，拼上根目录读文件） */
    private final Path imageRoot;

    public HouseImageSearchService(PythonAiClient pythonAiClient,
                                   HouseImageMapper houseImageMapper,
                                   HouseService houseService,
                                   @Value("${ai.house-image.root:}") String imageRootStr) {
        this.pythonAiClient = pythonAiClient;
        this.houseImageMapper = houseImageMapper;
        this.houseService = houseService;
        this.imageRoot = imageRootStr != null && !imageRootStr.isBlank()
                ? Path.of(imageRootStr) : null;
    }

    // ==================== 入库 ====================

    /**
     * 批量入库房源封面图（管理员触发）
     * @param limit 最多处理房源数（演示用 500 足够，全量 3002 也就几分钟）
     */
    public Map<String, Object> reindex(int limit) {
        if (imageRoot == null) {
            throw new IllegalStateException("未配置 ai.house-image.root（房源图片物理目录）");
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        int houseCount = 0, indexedImages = 0, skipped = 0;
        Map<String, String> urlToVectorId = new HashMap<>();   // 同图去重

        int page = 1, size = 50;
        while (houseCount < limit) {
            // ⚠️ 必须用可变 Map：HouseServiceImpl.findAll 内部会 put 默认查询条件，
            //    Map.of() 是不可变的会抛 UnsupportedOperationException（实测坑）
            List<HouseVO> houses = houseService.findAll(new HashMap<>(), page, size).getList();
            if (houses.isEmpty()) {
                break;
            }
            for (HouseVO h : houses) {
                if (houseCount >= limit) break;
                String cover = h.getCoverImage();
                if (cover == null || cover.isBlank()) { skipped++; continue; }

                try {
                    // 同图去重：一张物理图只 CLIP 编码一次
                    String vectorId = urlToVectorId.get(cover);
                    if (vectorId == null) {
                        Path file = imageRoot.resolve(cover.startsWith("/") ? cover.substring(1) : cover);
                        if (!Files.exists(file)) { skipped++; continue; }
                        byte[] bytes = Files.readAllBytes(file);
                        String filename = file.getFileName().toString();
                        // caption = 房源标题（文搜图命中靠它 —— 讲义05：图片没文字，caption 是桥梁）
                        vectorId = pythonAiClient.clipIndex(filename, bytes, cover,
                                h.getTitle() + " " + h.getCity() + h.getDistrict());
                        urlToVectorId.put(cover, vectorId);
                        indexedImages++;
                    }
                    // 每个房源一条 house_image 记录（多房源可共享同一 vector_id）
                    HouseImage img = new HouseImage();
                    img.setHouseId(h.getId());
                    img.setImageUrl(cover);
                    img.setCaption(h.getTitle());
                    img.setVectorId(vectorId);
                    houseImageMapper.insert(img);
                    houseCount++;
                } catch (Exception e) {
                    log.warn("房源[{}]图片入库失败: {}", h.getId(), e.getMessage());
                    skipped++;
                }
            }
            page++;
        }
        stats.put("houses", houseCount);
        stats.put("uniqueImages", indexedImages);
        stats.put("skipped", skipped);
        log.info("CLIP 入库完成: {}", stats);
        return stats;
    }

    // ==================== 检索 ====================

    /** 图搜图 */
    public List<Map<String, Object>> searchByImage(String filename, byte[] imageBytes, int topK) {
        List<PythonAiClient.ClipHit> hits = pythonAiClient.clipSearchImage(filename, imageBytes, topK);
        return toHouseResults(hits);
    }

    /** 文搜图 */
    public List<Map<String, Object>> searchByText(String description, int topK) {
        List<PythonAiClient.ClipHit> hits = pythonAiClient.clipSearchText(description, topK);
        return toHouseResults(hits);
    }

    /** ClipHit（图片维度）→ 房源维度结果（一张图可能对应多个房源，去重房源 id） */
    private List<Map<String, Object>> toHouseResults(List<PythonAiClient.ClipHit> hits) {
        List<Map<String, Object>> results = new ArrayList<>();
        LinkedHashSet<Long> seenHouses = new LinkedHashSet<>();

        for (PythonAiClient.ClipHit hit : hits) {
            List<HouseImage> images = houseImageMapper.findByVectorIds(List.of(hit.vectorId()));
            for (HouseImage img : images) {
                if (img.getHouseId() == null || img.getHouseId() == 0L) continue;   // 合同图片不入房源结果
                if (!seenHouses.add(img.getHouseId())) continue;
                try {
                    HouseVO h = houseService.getDetail(img.getHouseId());
                    if (h == null || !"APPROVED".equals(h.getStatus())) continue;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("houseId", h.getId());
                    m.put("title", h.getTitle());
                    m.put("city", h.getCity());
                    m.put("district", h.getDistrict());
                    m.put("price", h.getPrice());
                    m.put("similarity", Math.round(hit.score() * 1000) / 1000.0);
                    m.put("coverImage", h.getCoverImage());
                    results.add(m);
                } catch (Exception e) {
                    log.warn("反查房源[{}]失败: {}", img.getHouseId(), e.getMessage());
                }
            }
        }
        return results;
    }
}
