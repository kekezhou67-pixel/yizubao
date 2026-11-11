package com.rental.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 文件存储配置 —— 从 application.properties 读取
 */
@Configuration
@ConfigurationProperties(prefix = "file")
public class FileStorageConfig {

    /** 文件存储根路径 */
    private String storagePath = System.getProperty("user.home") + "/rental-platform/files";

    /** 文件下载基础URL */
    private String downloadBaseUrl = "http://localhost:8080";

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public String getDownloadBaseUrl() { return downloadBaseUrl; }
    public void setDownloadBaseUrl(String downloadBaseUrl) { this.downloadBaseUrl = downloadBaseUrl; }
}
