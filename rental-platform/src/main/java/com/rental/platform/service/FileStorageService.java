package com.rental.platform.service;

/**
 * 文件存储服务 —— 管理生成文件的存取
 */
public interface FileStorageService {

    /**
     * 存储文件
     * @param bytes 文件字节
     * @param subDir 子目录（如 docx, pdf）
     * @param filename 文件名
     * @return 文件相对路径
     */
    String store(byte[] bytes, String subDir, String filename);

    /**
     * 读取文件
     * @param relativePath 相对路径
     * @return 文件字节，文件不存在返回null
     */
    byte[] retrieve(String relativePath);

    /**
     * 生成下载URL
     * @param relativePath 相对路径
     * @return 完整下载URL
     */
    String getDownloadUrl(String relativePath);

    /**
     * 删除文件
     * @param relativePath 相对路径
     * @return 是否成功
     */
    boolean delete(String relativePath);
}
