package com.mo.corecraft.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;

/**
 * S3 兼容对象存储实现 —— RustFS / MinIO / SeaweedFS / Ceph RGW / 各家云 OSS 共用这一份。
 *
 * <p>**返回的 key 和本地实现完全一样**（{@code <scope>/<uuid><ext>}），
 * 所以切换存储时正文里的 markdown 链接一个字都不用改。
 */
@Slf4j
@RequiredArgsConstructor
public class S3FileStorage implements FileStorage {

    private final StorageProperties properties;
    private final S3Client client;

    @Override
    public String put(Long userId, String scope, MultipartFile file) {
        ObjectFiles.requireUploadAllowed(file, properties.maxSizeBytes());
        String key = ObjectFiles.keyOf(userId, scope, file.getOriginalFilename());
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key)
                            // 原文类型给对了浏览器才能内联渲染；给错了它会当附件下载。
                            // **这是"提示"不是"决定"** —— 真正决定内联还是下载的是
                            // FileController 读的时候按扩展名判的那张白名单
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new RuntimeException("文件读取失败", e);
        } catch (S3Exception e) {
            throw new RuntimeException("对象存储写入失败：" + errorOf(e), e);
        }
        log.info("对象已写入 bucket={} key={}（{} 字节）", properties.getBucket(), key, file.getSize());
        return key;
    }

    /**
     * {@code getObject} 的响应体是**一次性**的流，包成 {@link InputStreamResource}
     * 直接交给 Spring 输出。这里不能缓存 —— 同一个 Resource 被读第二次就是空的，
     * 而那种错只在并发读同一张图时偶发，排查起来极其费劲。
     */
    @Override
    public Resource load(String path) {
        try {
            return new InputStreamResource(client.getObject(GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(path)
                    .build()));
        } catch (NoSuchKeyException e) {
            throw new IllegalArgumentException("文件不存在");
        } catch (S3Exception e) {
            // 404 之外也可能没有 awsErrorDetails（连接层面的失败），别直接 .errorMessage()
            throw new RuntimeException("对象存储读取失败：" + errorOf(e), e);
        }
    }

    /**
     * 把 {@link S3Exception} 变成一句人能看懂的话。
     *
     * <p>⚠️ **不能只判 {@code awsErrorDetails()} 是不是 null** —— SDK 会给出
     * 「details 存在、但里面的 message 是 null」这种情况（2026-10-18 踩到：
     * RustFS 返回的 404 响应体老 SDK 解析不出 message，于是包装出来的是
     * 字面量 {@code "对象存储写入失败：null"}，比原始的 S3Exception 还少信息 ——
     * 后者至少还带着 {@code Status Code: 404}）。
     *
     * <p>三级兜底：**errorCode 最有用**（{@code NoSuchBucket} / {@code AccessDenied}），
     * 其次 message，最后退到 HTTP 状态码。任何一级都不能交出 null。
     */
    private String errorOf(S3Exception e) {
        AwsErrorDetails d = e.awsErrorDetails();
        if (d != null && d.errorCode() != null && !d.errorCode().isBlank()) {
            return d.errorCode();
        }
        if (d != null && d.errorMessage() != null && !d.errorMessage().isBlank()) {
            return d.errorMessage();
        }
        return "HTTP " + e.statusCode();
    }
}
