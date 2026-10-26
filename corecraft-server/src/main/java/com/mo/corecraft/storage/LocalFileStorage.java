package com.mo.corecraft.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘实现。
 *
 * <p>⚠️ **没有 {@code @Component}**：实例由 {@link FileStorageConfig} 按
 * {@link StorageProperties#getKind()} 决定。留着注解会出现两个 FileStorage Bean
 * （这个 + 配置类产出的），注入时报 {@code NoUniqueBeanDefinitionException}，
 * 而那是个只在启动时才炸、看不出跟"多写了个注解"有关的错。
 */
@Slf4j
@RequiredArgsConstructor
public class LocalFileStorage implements FileStorage {

    private final StorageProperties properties;

    @Override
    public String put(Long userId, String scope, MultipartFile file) {
        ObjectFiles.requireUploadAllowed(file, properties.maxSizeBytes());
        String key = ObjectFiles.keyOf(userId, scope, file.getOriginalFilename());
        // key 是 `u/<userId>/<scope>/<name>`，落盘时**去掉最前面的 `u/<userId>`**：
        // 本地盘已经是每个用户一份部署，多套一层没有意义。
        int cut = key.indexOf('/', 2) + 1;
        String relative = key.substring(cut);
        Path dir = properties.rootPath().resolve(relative).getParent();
        Path target = properties.rootPath().resolve(relative);
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败", e);
        }
        log.info("文件已保存到本地 {}（{} 字节）", key, file.getSize());
        return key;
    }

    @Override
    public Resource load(String path) {
        Path root = properties.rootPath();
        Path target = root.resolve(path).normalize();
        // ⚠️ **越界检查不能省**：`/files/../application-dev.yml` 这类 URL 会被
        // `resolve` 照单全收，直接读到存储根目录外面的文件上去。
        // normalize 之后必须还在 root 底下。
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法路径");
        }
        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("文件不存在");
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("文件路径非法", e);
        }
    }
}
