package com.rental.platform.controller;

import com.rental.platform.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件下载控制器 —— 提供生成文档的下载端点
 */
@RestController
@RequestMapping("/api/file")
public class FileDownloadController {

    private static final Logger log = LoggerFactory.getLogger(FileDownloadController.class);

    private final FileStorageService fileStorageService;

    public FileDownloadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * 下载文件
     * GET /api/file/download/{filename}
     * 支持路径中的子目录: /api/file/download/docx/2026/07/file.docx
     */
    @GetMapping("/download/**")
    public ResponseEntity<byte[]> download(jakarta.servlet.http.HttpServletRequest request) {
        // 提取完整路径（/api/file/download/ 之后的部分）
        String fullPath = request.getRequestURI();
        String prefix = "/api/file/download/";
        String relativePath = fullPath.substring(fullPath.indexOf(prefix) + prefix.length());

        log.info("文件下载请求: {}", relativePath);

        byte[] bytes = fileStorageService.retrieve(relativePath);
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }

        // 提取纯文件名
        String filename = relativePath.substring(relativePath.lastIndexOf('/') + 1);

        // 确定Content-Type
        MediaType mediaType;
        if (filename.endsWith(".docx")) {
            mediaType = MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        } else if (filename.endsWith(".pdf")) {
            mediaType = MediaType.APPLICATION_PDF;
        } else {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        (filename.endsWith(".pdf") && "true".equals(request.getParameter("inline")) ? "inline" : "attachment") + "; filename*=UTF-8''" + encodedFilename)
                .body(bytes);
    }

    /** Extract copyable text from earlier generated files as well. */
    @GetMapping("/text/**")
    public ResponseEntity<Map<String,Object>> text(jakarta.servlet.http.HttpServletRequest request) {
        String prefix="/api/file/text/";
        String path=request.getRequestURI().substring(request.getRequestURI().indexOf(prefix)+prefix.length());
        byte[] bytes=fileStorageService.retrieve(path);
        if(bytes==null)return ResponseEntity.notFound().build();
        if(bytes.length>20*1024*1024)return ResponseEntity.status(413).body(Map.of("message","文件较大，请下载后查看"));
        try {
            StringBuilder text=new StringBuilder();
            if(path.endsWith(".pdf")) {
                com.lowagie.text.pdf.PdfReader reader=new com.lowagie.text.pdf.PdfReader(bytes);
                try {var extractor=new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);for(int i=1;i<=Math.min(reader.getNumberOfPages(),100);i++)text.append(extractor.getTextFromPage(i)).append("\n\n");} finally {reader.close();}
            } else if(path.endsWith(".docx")) {
                try(var document=new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(bytes));var extractor=new org.apache.poi.xwpf.extractor.XWPFWordExtractor(document)){text.append(extractor.getText());}
            } else return ResponseEntity.badRequest().body(Map.of("message","仅支持 PDF 或 Word 文件"));
            return ResponseEntity.ok(Map.of("text",text.toString()));
        } catch(Exception e) {return ResponseEntity.unprocessableEntity().body(Map.of("message","暂时无法提取文字，可预览或下载文件"));}
    }

    /**
     * 获取文件信息（检查文件是否存在）
     */
    @GetMapping("/info/**")
    public ResponseEntity<Map<String, Object>> info(jakarta.servlet.http.HttpServletRequest request) {
        String fullPath = request.getRequestURI();
        String prefix = "/api/file/info/";
        String relativePath = fullPath.substring(fullPath.indexOf(prefix) + prefix.length());

        byte[] bytes = fileStorageService.retrieve(relativePath);
        Map<String, Object> result = new HashMap<>();

        if (bytes == null) {
            result.put("exists", false);
        } else {
            String filename = relativePath.substring(relativePath.lastIndexOf('/') + 1);
            result.put("exists", true);
            result.put("filename", filename);
            result.put("fileSize", bytes.length);
            result.put("downloadUrl", fileStorageService.getDownloadUrl(relativePath));
        }

        return ResponseEntity.ok(result);
    }
}
