package com.mo.corecraft.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * 按 {@code app.storage.kind} 产出唯一的 {@link FileStorage}。
 *
 * <h3>为什么用 switch 而不是 {@code @ConditionalOnProperty}</h3>
 * 后者要在两个实现类上各写一次 {@code @ConditionalOnProperty(havingValue="local")}，
 * 于是 {@code "local"} / {@code "s3"} 这些字面量在**两处注解 + 一个枚举**里各出现一遍。
 * 而配置里写错一个值的后果是"**没有任何 Bean 被创建**"——
 * 启动时报 {@code NoSuchBeanDefinitionException: FileStorage}，看不出是哪个字打错了。
 *
 * <p>switch 这一处把 {@link StorageKind} 变成**唯一权威**：值不在枚举里，
 * Spring 绑定配置时就直接失败了，错误信息里就带着那个字面量。
 */
@Configuration
@RequiredArgsConstructor
public class FileStorageConfig {

    private final StorageProperties properties;

    @Bean
    public FileStorage fileStorage() {
        return switch (properties.getKind()) {
            case LOCAL -> new LocalFileStorage(properties);
            case S3 -> new S3FileStorage(properties, s3Client());
        };
    }

    /**
     * S3 客户端**只在选 S3 档时才建** —— 走 LOCAL 的人不会为了一个用不到的客户端
     * 去付连接池和凭证解析的启动开销。
     */
    private S3Client s3Client() {
        return S3Client.builder()
                // ⚠️ **HTTP 实现必须显式给，不能让它在 classpath 上猜**。
                // AWS SDK 碰到两个及以上实现会直接抛
                // `SdkClientException: Multiple HTTP implementations were found on the classpath`。
                // pom 里已经排掉了 apache-client 和 netty-nio-client，但那是**防体积**的；
                // 这一行是**防将来**的 —— 哪天有人加了另一个带 AWS SDK 的依赖、
                // 排除失效了，这里仍然是确定的，不用等到启动才炸。
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                // 自建的对象存储基本都要 path-style：virtual-host 方式要求桶名是个
                // 能解析的域名，`rustfs103` 解析不了。设成 false 的症状是
                // UnknownHostException，而报错里完全看不出是这里。
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyle())
                        .build())
                .build();
    }
}
