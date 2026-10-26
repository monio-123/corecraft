package com.mo.corecraft.storage;

/**
 * 存储类型。**只有两个值，不按厂商拆。**
 *
 * <h3>为什么不拆成 LOCAL / MINIO / RUSTFS / SEAWEEDFS</h3>
 * 这几家用的是**同一种协议**（S3 API），{@link FileStorage} 只要 put / load 两个方法，
 * 一份 S3 代码全都能跑。按厂商拆的结果是三个**走同一段逻辑**的枚举值，
 * 也就是三份必须永远保持同步的分支 —— 而它们之间的差别只是 endpoint，
 * 那是**配置**不是**代码**。
 *
 * <p>厂商差异真要进代码的判据是「它有 S3 之外的独有能力而我们真用上了」
 * （预签名策略、IAM 角色、对象锁……）。现在一个都没有。
 *
 * <h3>为什么 LOCAL 必须留着</h3>
 * 打包成镜像发出去时，**不能要求使用者另外再起一个对象存储**。
 * {@link #LOCAL} 就是那个「零外部依赖」的档：一条知识库的图片全在服务器本地磁盘上。
 * 这也是本项目规模下的默认档。
 */
public enum StorageKind {

    /** 本地磁盘。`app.storage.root` 之下按用途分目录（avatars / contents） */
    LOCAL,

    /**
     **所有 S3 兼容的对象存储** —— RustFS / MinIO / SeaweedFS / Ceph RGW / 各家云 OSS。
     具体是哪一家由 `app.storage.endpoint` 决定，见 {@link StorageProperties}。
     */
    S3
}
