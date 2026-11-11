package com.rental.platform.service;

import com.rental.platform.dto.GeneratedFileInfo;
import java.util.List;
import java.util.Map;

/**
 * 文档生成服务 —— 生成 Word (.docx) 和 PDF 文档
 */
public interface DocumentGenerationService {

    /**
     * 生成 Word 文档 (.docx)
     * @param title 文档标题
     * @param sections 内容段落 [{heading, body, tableData?}]
     * @param userId 用户ID
     * @return 文件信息（含下载URL）
     */
    GeneratedFileInfo generateDocx(String title, List<Map<String, Object>> sections, Long userId);

    /**
     * 生成 PDF 文档
     * @param title 文档标题
     * @param sections 内容段落
     * @param userId 用户ID
     * @return 文件信息（含下载URL）
     */
    GeneratedFileInfo generatePdf(String title, List<Map<String, Object>> sections, Long userId);
}
