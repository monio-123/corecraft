package com.mo.corecraft.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;

/**
 * 存储配置（{@code app.storage.*}）。
 *
 * <p>⚠️ **正式值在 Nacos 上**，本地的 {@code application-dev.yml} 只是留个样子。
 *
 * <h3>两档配置各自要填什么</h3>
 * <pre>
 * kind: local          → 只用 root
 * kind: s3             → 用 endpoint / region / bucket / accessKey / secretKey / pathStyle
 * </pre>
 * 另一档的字段**留着不填也没关系**，代码不会去读它们（见 {@link FileStorageConfig}）。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /**
     * 用哪种存储。**缺省 LOCAL** —— 没配就落本地盘，服务照常起得来。
     *
     * <p>不配默认值是刻意的：配置在 Nacos 上，Nacos 拉不到时这一段根本走不到，
     * 那时因为一个 null 抛 NPE、整个服务起不来，是把「忘配一个路径」放大成事故。
     */
    private StorageKind kind = StorageKind.LOCAL;

    // ==================== LOCAL ====================

    /**
     * 本地存储根目录（{@code kind=local} 时才读）。
     *
     * <p>相对路径按工作目录解析。默认落在工作目录的 {@code storage/} 下。
     */
    private String root = System.getProperty("user.dir") + "/storage";

    /** 根目录的规范化形式，越界检查和拼路径都用它 */
    public Path rootPath() {
        return Path.of(root).toAbsolutePath().normalize();
    }

    // ==================== S3 ====================

    /**
     * S3 服务地址（{@code kind=s3} 时才读）。
     * 容器之间用服务名：<pre>http://rustfs103:9000</pre>
     */
    private String endpoint;

    /**
     * 区域。**自建的对象存储基本都不校验它**，但 SDK 签名时需要，所以必须给一个。
     * 同 `app.storage.*` 的其它配置一样，正式值在 Nacos。
     */
    private String region = "us-east-1";

    /** 桶名。对象落在 {@code <bucket>/<scope>/<uuid>.<ext>} */
    private String bucket;

    private String accessKey;

    private String secretKey;

    /**
     * 是否用 path-style（{@code http://host/bucket/key}）而不是 virtual-host
     * （{@code http://bucket.host/key}）。
     *
     * <p><b>自建的对象存储基本都要 true</b>：virtual-host 方式要求
     * {@code rustfs103} 是个能解析的域名，容器名解析不了。设成 false 会得到
     * 一堆莫名其妙的 DNS/证书错误，而 S3 客户端那句 "UnknownHostException: rustfs103"
     * 很难让人联想到是这里。
     */
    /**
     * 单个上传文件的大小上限。
     *
     * <p>**一处，不按文件类别分级** —— 语雀把「视频/压缩包」划成付费格式，
     * 那是它要跑转码服务的理由；我们不转码，就没有分级的理由。
     * 真要分级，那是先有「视频单独走一套流程」这件事之后再说。
     *
     * <p>⚠️ **类型必须是 {@link DataSize} 而不是 {@code long}**：配置在 Nacos 上，
     * 写成 {@code max-size: 20MB} 是人能读懂的写法；而 `long` 字段收到字符串
     * {@code "20MB"} 会转换失败（2026-10-18 用 Spring Boot 3.2.8 的真实转换器实测：
     * {@code convert("20MB", Long.class)} → ConversionFailedException）。
     * 这个失败发生在 {@code @ConfigurationProperties} 绑定阶段，每次 Nacos 推送都炸一次，
     * 而且**炸了 md5 提交不了 → 下一轮轮询又判定成"有变更"**，于是变成每几秒刷一条的死循环。
     * 裸数字（{@code 20971520}）两种类型都认，{@code DataSize} 额外认 {@code 20MB}。
     */
    private DataSize maxSize = DataSize.ofMegabytes(20);

    /** 上限的字节数。校验那一步比的是 {@code MultipartFile.getSize()}，所以对外只给字节 */
    public long maxSizeBytes() {
        return maxSize.toBytes();
    }

    private boolean pathStyle = true;
}
