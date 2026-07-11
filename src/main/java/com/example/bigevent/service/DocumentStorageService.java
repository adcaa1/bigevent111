package com.example.bigevent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文档本地存储服务
 * <p>
 * 按日期分目录存储上传文件，返回相对路径，便于后续重新读取和删除。
 */
@Slf4j
@Service
public class DocumentStorageService {

    @Value("${rag.document.upload-path:uploads}")
    private String uploadPath;

    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM");

    /**
     * 保存文件到本地，返回相对路径
     */
    public String store(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);
        String storageName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);

        LocalDate now = LocalDate.now();
        Path targetDir = Paths.get(uploadPath, now.format(YEAR_FORMATTER), now.format(MONTH_FORMATTER));
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        Path targetPath = targetDir.resolve(storageName);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // 返回相对路径，例如 uploads/2026/07/xxx.pdf
        return Paths.get(uploadPath, now.format(YEAR_FORMATTER), now.format(MONTH_FORMATTER), storageName)
                .toString()
                .replace("\\", "/");
    }

    /**
     * 根据相对路径读取文件
     */
    public InputStream load(String relativePath) throws IOException {
        Path path = Paths.get(relativePath);
        return Files.newInputStream(path);
    }

    /**
     * 根据相对路径删除文件
     */
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Path path = Paths.get(relativePath);
            boolean deleted = Files.deleteIfExists(path);
            if (deleted) {
                log.info("已删除本地文件: {}", relativePath);
            }
        } catch (IOException e) {
            log.warn("删除本地文件失败: {}", relativePath, e);
        }
    }

    /**
     * 计算 MultipartFile 的 MD5
     */
    public String computeMd5(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            return computeMd5(is);
        }
    }

    /**
     * 根据相对路径计算文件 MD5
     */
    public String computeMd5(String relativePath) throws IOException {
        try (InputStream is = load(relativePath)) {
            return computeMd5(is);
        }
    }

    private String computeMd5(InputStream is) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                md.update(buffer, 0, len);
            }
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 算法不可用", e);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
