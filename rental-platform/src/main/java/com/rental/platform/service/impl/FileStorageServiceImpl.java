package com.rental.platform.service.impl;

import com.rental.platform.config.FileStorageConfig;
import com.rental.platform.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 文件存储服务实现 —— 本地磁盘存储
 */
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageServiceImpl.class);

    private final Path rootPath;
    private final String downloadBaseUrl;

    public FileStorageServiceImpl(FileStorageConfig config) {
        this.rootPath = Paths.get(config.getStoragePath());
        this.downloadBaseUrl = config.getDownloadBaseUrl();
        // 确保根目录存在
        try {
            Files.createDirectories(rootPath);
            log.info("文件存储根目录: {}", rootPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("创建文件存储目录失败: {}", e.getMessage());
        }
    }

    @Override
    public String store(byte[] bytes, String subDir, String filename) {
        try {
            // 按年月组织子目录: docx/2026/07/
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
            Path dirPath = rootPath.resolve(subDir).resolve(datePath);
            Files.createDirectories(dirPath);

            Path filePath = dirPath.resolve(filename);
            Files.write(filePath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("文件已存储: {}", filePath.toAbsolutePath());

            // 返回相对路径: docx/2026/07/xxx.docx
            return subDir + "/" + datePath + "/" + filename;
        } catch (IOException e) {
            log.error("文件存储失败: {}", e.getMessage());
            throw new RuntimeException("文件存储失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] retrieve(String relativePath) {
        try {
            Path filePath = rootPath.resolve(relativePath);
            // 安全检查：防止路径遍历攻击
            if (!filePath.normalize().startsWith(rootPath.normalize())) {
                log.warn("非法文件访问路径: {}", relativePath);
                return null;
            }
            if (!Files.exists(filePath)) {
                log.warn("文件不存在: {}", filePath);
                return null;
            }
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            log.error("文件读取失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String getDownloadUrl(String relativePath) {
        return downloadBaseUrl + "/api/file/download/" + relativePath.replace("\\", "/");
    }

    @Override
    public boolean delete(String relativePath) {
        try {
            Path filePath = rootPath.resolve(relativePath);
            if (!filePath.normalize().startsWith(rootPath.normalize())) {
                return false;
            }
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("文件删除失败: {}", e.getMessage());
            return false;
        }
    }
}
