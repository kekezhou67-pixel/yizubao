package com.rental.platform.controller;

import com.rental.platform.client.PythonAiClient;
import com.rental.platform.config.UserContextHolder;
import com.rental.platform.entity.ContractReview;
import com.rental.platform.entity.HouseImage;
import com.rental.platform.mapper.ContractReviewMapper;
import com.rental.platform.mapper.HouseImageMapper;
import com.rental.platform.service.ContractReviewService;
import com.rental.platform.service.FileStorageService;
import com.rental.platform.utils.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contract")
public class ContractReviewController {

    private static final Logger log = LoggerFactory.getLogger(ContractReviewController.class);

    private final ContractReviewService contractReviewService;
    private final ContractReviewMapper contractReviewMapper;
    private final PythonAiClient pythonAiClient;
    private final FileStorageService fileStorageService;
    private final HouseImageMapper houseImageMapper;

    @Autowired
    public ContractReviewController(
            @Autowired(required = false) ContractReviewService contractReviewService,
            ContractReviewMapper contractReviewMapper,
            PythonAiClient pythonAiClient,
            FileStorageService fileStorageService,
            HouseImageMapper houseImageMapper) {
        this.contractReviewService = contractReviewService;
        this.contractReviewMapper = contractReviewMapper;
        this.pythonAiClient = pythonAiClient;
        this.fileStorageService = fileStorageService;
        this.houseImageMapper = houseImageMapper;
    }

    /**
     * 合同审查
     */
    @PostMapping("/review")
    public Result<Map<String, Object>> review(@RequestBody Map<String, Object> params) {
        String contentText = (String) params.get("contentText");
        Long houseId = params.get("houseId") != null ? Long.valueOf(params.get("houseId").toString()) : null;
        Long userId = UserContextHolder.getCurrentUserId();

        if (contentText == null || contentText.trim().isEmpty()) {
            return Result.error("合同文本内容不能为空");
        }

        if (contractReviewService == null) {
            return Result.error("AI 服务未配置，合同审查功能暂不可用");
        }

        Map<String, Object> result = contractReviewService.reviewContract(contentText, userId);

        // 检查服务层是否返回错误
        if (result.containsKey("success") && Boolean.FALSE.equals(result.get("success"))) {
            return Result.error((String) result.getOrDefault("message", "审查失败"));
        }

        // 关联房源（仅更新 house_id，不覆盖其他字段）
        if (houseId != null && result.containsKey("id")) {
            contractReviewMapper.updateHouseId(
                    Long.valueOf(result.get("id").toString()), houseId);
        }

        return Result.success("审查完成", result);
    }

    /**
     * 获取当前用户的审查列表
     */
    @GetMapping("/list")
    public Result<List<ContractReview>> list() {
        List<ContractReview> reviews = contractReviewMapper.listByUser(UserContextHolder.getCurrentUserId());
        return Result.success(reviews);
    }

    /**
     * 获取单条审查详情
     */
    @GetMapping("/detail/{id}")
    public Result<ContractReview> detail(@PathVariable Long id) {
        ContractReview review = contractReviewMapper.findById(id);
        if (review == null) {
            return Result.error("审查记录不存在");
        }
        return Result.success(review);
    }

    /**
     * 合同图片审查（AI 改造 · 阶段7A）：拍照/扫描件也能审
     * POST /api/contract/review-image （multipart: file）
     *
     * 链路（工程方案 §7A.2）：
     *   ① 存图（FileStorageService）→ ② Python PaddleOCR 识别文字
     *   → ③ 复用阶段6 RAG 审查（每条风险带法条依据）→ ④ 记录存 house_image（ocr_text）
     */
    @PostMapping("/review-image")
    public Result<Map<String, Object>> reviewImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("请上传合同图片");
        }
        if (contractReviewService == null) {
            return Result.error("AI 服务未配置，合同审查功能暂不可用");
        }
        String filename = file.getOriginalFilename();
        String lower = filename != null ? filename.toLowerCase() : "";
        if (!lower.endsWith(".jpg") && !lower.endsWith(".jpeg") && !lower.endsWith(".png")
                && !lower.endsWith(".bmp") && !lower.endsWith(".webp")) {
            return Result.error("仅支持 jpg/png/bmp/webp 图片格式");
        }
        try {
            Long userId = UserContextHolder.getCurrentUserId();
            byte[] bytes = file.getBytes();

            // ① 存图
            String relativePath = fileStorageService.store(bytes, "contract",
                    System.currentTimeMillis() + "_" + filename);
            String imageUrl = fileStorageService.getDownloadUrl(relativePath);

            // ② OCR（首次调用 PaddleOCR 要下载模型，可能较慢）
            String ocrText;
            try {
                ocrText = pythonAiClient.ocr(filename, bytes);
            } catch (Exception e) {
                log.error("OCR 识别失败: {}", e.getMessage());
                return Result.error("图片文字识别失败: " + e.getMessage());
            }
            if (ocrText == null || ocrText.trim().length() < 10) {
                return Result.error("图片中未识别到有效文字，请上传更清晰的合同照片");
            }

            // ③ 复用 RAG 审查链路
            Map<String, Object> reviewResult = contractReviewService.reviewContract(ocrText, userId);
            if (reviewResult.containsKey("success") && Boolean.FALSE.equals(reviewResult.get("success"))) {
                return Result.error((String) reviewResult.getOrDefault("message", "审查失败"));
            }

            // ④ 记录图片与 OCR 文本（house_id=0 表示不挂房源的合同图片）
            try {
                HouseImage img = new HouseImage();
                img.setHouseId(0L);
                img.setImageUrl(imageUrl);
                img.setOcrText(ocrText);
                houseImageMapper.insert(img);
            } catch (Exception e) {
                log.warn("合同图片记录保存失败（不影响审查结果）: {}", e.getMessage());
            }

            // ⑤ 返回：审查结果 + ocrText + imageUrl（工程方案 §5.3 响应结构）
            Map<String, Object> result = new HashMap<>(reviewResult);
            result.put("ocrText", ocrText);
            result.put("imageUrl", imageUrl);
            result.put("risks", reviewResult.get("riskPoints"));   // 方案字段名兼容
            return Result.success(result);
        } catch (Exception e) {
            log.error("合同图片审查失败: {}", e.getMessage(), e);
            return Result.error("合同图片审查失败: " + e.getMessage());
        }
    }
}

