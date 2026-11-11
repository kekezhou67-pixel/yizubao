package com.rental.platform.dto;

import java.time.LocalDateTime;

/**
 * 生成文件信息 DTO —— 返回给前端展示和下载
 */
public class GeneratedFileInfo {

    /** 文件名 */
    private String filename;

    /** 下载URL */
    private String downloadUrl;

    /** 文件大小（字节） */
    private long fileSize;

    /** 文件类型：docx / pdf */
    private String fileType;

    /** 文档标题 */
    private String title;

    /** 生成时间 */
    private LocalDateTime createdAt;

    public GeneratedFileInfo() {}

    public GeneratedFileInfo(String filename, String downloadUrl, long fileSize, String fileType, String title) {
        this.filename = filename;
        this.downloadUrl = downloadUrl;
        this.fileSize = fileSize;
        this.fileType = fileType;
        this.title = title;
        this.createdAt = LocalDateTime.now();
    }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
