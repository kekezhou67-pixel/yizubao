package com.rental.platform.service.impl;

import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.rental.platform.dto.GeneratedFileInfo;
import com.rental.platform.service.DocumentGenerationService;
import com.rental.platform.service.FileStorageService;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 文档生成服务实现
 * - Word: Apache POI XWPFDocument
 * - PDF: OpenPDF (com.lowagie.text)
 */
@Service
public class DocumentGenerationServiceImpl implements DocumentGenerationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentGenerationServiceImpl.class);
    private final FileStorageService fileStorageService;

    public DocumentGenerationServiceImpl(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @Override
    public GeneratedFileInfo generateDocx(String title, List<Map<String, Object>> sections, Long userId) {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            // === 标题 ===
            XWPFParagraph titlePara = doc.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText(title);
            titleRun.setBold(true);
            titleRun.setFontSize(18);
            titleRun.setFontFamily("微软雅黑");
            titleRun.addBreak();

            // === 生成时间 ===
            XWPFParagraph datePara = doc.createParagraph();
            datePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun dateRun = datePara.createRun();
            dateRun.setText("生成时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            dateRun.setFontSize(10);
            dateRun.setColor("999999");
            dateRun.setFontFamily("微软雅黑");
            dateRun.addBreak();

            // === 分隔线 ===
            XWPFParagraph hr = doc.createParagraph();
            XWPFRun hrRun = hr.createRun();
            hrRun.setText("—".repeat(40));
            hrRun.setColor("cccccc");
            hrRun.setFontSize(8);

            // === 内容段落 ===
            for (Map<String, Object> section : sections) {
                String heading = (String) section.getOrDefault("heading", "");
                String body = (String) section.getOrDefault("body", "");
                Object tableData = section.get("tableData");

                // 段落标题
                if (heading != null && !heading.isEmpty()) {
                    XWPFParagraph headingPara = doc.createParagraph();
                    headingPara.setSpacingBefore(200);
                    XWPFRun headingRun = headingPara.createRun();
                    headingRun.setText(heading);
                    headingRun.setBold(true);
                    headingRun.setFontSize(14);
                    headingRun.setFontFamily("微软雅黑");
                }

                // 段落正文
                if (body != null && !body.isEmpty()) {
                    XWPFParagraph bodyPara = doc.createParagraph();
                    XWPFRun bodyRun = bodyPara.createRun();
                    bodyRun.setText(body);
                    bodyRun.setFontSize(11);
                    bodyRun.setFontFamily("微软雅黑");
                }

                // 表格
                if (tableData instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<List<String>> rows = (List<List<String>>) tableData;
                    if (!rows.isEmpty()) {
                        XWPFTable table = doc.createTable(rows.size(), rows.get(0).size());
                        table.setWidth("100%");
                        for (int r = 0; r < rows.size(); r++) {
                            List<String> row = rows.get(r);
                            for (int c = 0; c < row.size() && c < rows.get(r).size(); c++) {
                                XWPFTableCell cell = table.getRow(r).getCell(c);
                                cell.setText(row.get(c));
                                // 表头加粗
                                if (r == 0) {
                                    cell.getParagraphs().get(0).getRuns().forEach(run -> {
                                        run.setBold(true);
                                        run.setFontSize(10);
                                    });
                                }
                            }
                        }
                    }
                }
            }

            // === 页脚 ===
            XWPFParagraph footerPara = doc.createParagraph();
            footerPara.setAlignment(ParagraphAlignment.CENTER);
            footerPara.setSpacingBefore(400);
            XWPFRun footerRun = footerPara.createRun();
            footerRun.setText("— 由易租宝 AI 助手自动生成 —");
            footerRun.setFontSize(9);
            footerRun.setColor("999999");
            footerRun.setItalic(true);

            doc.write(bos);
            byte[] bytes = bos.toByteArray();

            // 存储文件
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String filename = "doc_" + userId + "_" + timestamp + ".docx";
            String relativePath = fileStorageService.store(bytes, "docx", filename);
            String downloadUrl = fileStorageService.getDownloadUrl(relativePath);

            log.info("Word文档生成成功: {}, 大小: {} bytes", filename, bytes.length);
            return new GeneratedFileInfo(filename, downloadUrl, bytes.length, "docx", title);

        } catch (Exception e) {
            log.error("Word文档生成失败: {}", e.getMessage());
            throw new RuntimeException("Word文档生成失败: " + e.getMessage(), e);
        }
    }

    @Override
    public GeneratedFileInfo generatePdf(String title, List<Map<String, Object>> sections, Long userId) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            com.lowagie.text.Document document = new com.lowagie.text.Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, bos);
            document.open();

            // === 中文字体 ===
            BaseFont baseFont;
            try {
                baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            }
            Font titleFont = new Font(baseFont, 18, Font.BOLD, java.awt.Color.decode("#1f2937"));
            Font headingFont = new Font(baseFont, 13, Font.BOLD, java.awt.Color.decode("#4f46e5"));
            Font bodyFont = new Font(baseFont, 11, Font.NORMAL, java.awt.Color.decode("#374151"));
            Font smallFont = new Font(baseFont, 9, Font.ITALIC, java.awt.Color.decode("#999999"));
            Font tableHeaderFont = new Font(baseFont, 10, Font.BOLD);

            // === 标题 ===
            Paragraph titlePara = new Paragraph(title, titleFont);
            titlePara.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            titlePara.setSpacingAfter(6);
            document.add(titlePara);

            // === 生成时间 ===
            Paragraph datePara = new Paragraph(
                    "生成时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    smallFont);
            datePara.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            datePara.setSpacingAfter(16);
            document.add(datePara);

            // === 分隔线 ===
            Paragraph hr = new Paragraph("─".repeat(50), smallFont);
            hr.setSpacingAfter(12);
            document.add(hr);

            // === 内容 ===
            for (Map<String, Object> section : sections) {
                String heading = (String) section.getOrDefault("heading", "");
                String body = (String) section.getOrDefault("body", "");
                Object tableData = section.get("tableData");

                if (heading != null && !heading.isEmpty()) {
                    Paragraph hp = new Paragraph(heading, headingFont);
                    hp.setSpacingBefore(14);
                    hp.setSpacingAfter(6);
                    document.add(hp);
                }

                if (body != null && !body.isEmpty()) {
                    Paragraph bp = new Paragraph(body, bodyFont);
                    bp.setSpacingAfter(8);
                    document.add(bp);
                }

                if (tableData instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<List<String>> rows = (List<List<String>>) tableData;
                    if (!rows.isEmpty()) {
                        PdfPTable table = new PdfPTable(rows.get(0).size());
                        table.setWidthPercentage(100);
                        table.setSpacingBefore(8);
                        table.setSpacingAfter(12);

                        for (int r = 0; r < rows.size(); r++) {
                            List<String> row = rows.get(r);
                            for (String cell : row) {
                                PdfPCell pdfCell = new PdfPCell(new Phrase(cell != null ? cell : "",
                                        r == 0 ? tableHeaderFont : bodyFont));
                                pdfCell.setPadding(6);
                                if (r == 0) {
                                    pdfCell.setBackgroundColor(java.awt.Color.decode("#f3f4f6"));
                                }
                                table.addCell(pdfCell);
                            }
                        }
                        document.add(table);
                    }
                }
            }

            // === 页脚 ===
            Paragraph footerPara = new Paragraph("— 由易租宝 AI 助手自动生成 —", smallFont);
            footerPara.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            footerPara.setSpacingBefore(30);
            document.add(footerPara);

            document.close();
            byte[] bytes = bos.toByteArray();

            // 存储
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String filename = "pdf_" + userId + "_" + timestamp + ".pdf";
            String relativePath = fileStorageService.store(bytes, "pdf", filename);
            String downloadUrl = fileStorageService.getDownloadUrl(relativePath);

            log.info("PDF文档生成成功: {}, 大小: {} bytes", filename, bytes.length);
            return new GeneratedFileInfo(filename, downloadUrl, bytes.length, "pdf", title);

        } catch (Exception e) {
            log.error("PDF文档生成失败: {}", e.getMessage());
            throw new RuntimeException("PDF文档生成失败: " + e.getMessage(), e);
        }
    }
}
