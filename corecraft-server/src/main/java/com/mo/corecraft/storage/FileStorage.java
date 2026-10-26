package com.mo.corecraft.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储。**只有一个实现类在跑，换存储只改实现类。**
 *
 * <p>要换 MinIO / OSS / 七牛时，新增一个 {@code MinioFileStorage implements FileStorage}
 * 并把它注册成 Bean 即可，**调用方（上传接口、头像上传）一个字都不用改**。
 *
 * <p>## 为什么 DB 里只存路径
 *
 * 正文存的是 markdown 里的链接（{@code ![](/api/files/contents/xxx.png)}），不是图片本身。
 * 这不是偷懒，是 {@code TEXT} 列只有 65535 **字节**，而 base64 是二进制的 4/3 倍：
 * 一张 50KB 的截图 base64 后就是 67KB，单这一张就能把列撑爆，insert 直接报
 * {@code Data too long}。链接只有几十字节。
 *
 * <p>## 接口只留 put / load
 *
 * {@code delete} / {@code list} **故意没有** —— 现在没有调用者。
 * 将来真要删图（删除知识点时清理孤儿文件）或要做存储迁移遍历，
 * 再按那时候的需要加。加一个没人调的方法，等于凭空多一份没人维护的契约。
 *
 * <p>⚠️ 迁移到 MinIO 时**不需要 {@code list}**：那是一次性脚本，直接遍历本地目录重传即可，
 * 不用为了做一次性迁移把枚举能力塞进生产接口。
 */
public interface FileStorage {

    /**
     * 存一个文件，返回**相对路径**（不含前导斜杠、不含 host），形如
     * {@code u/1/contents/9f2a....png}。正文里存的就是它，前端拼 {@code /api/files/}。
     *
     * <p>返回相对路径而不是完整 URL，是因为**换存储时正文里的链接一个字都不用改** ——
     * 前端拼 {@code /api/files/} 前缀，域名和后端地址本来就在前端配置里。
     *
     * @param userId  所属用户。**由服务端从登录态取**，绝不是客户端传的 ——
     *                它决定文件落在哪个用户目录下，客户端传就能往别人那儿写
     * @param scope   业务分区（{@code avatars} / {@code contents}），白名单由调用方校验
     * @param file    上传的文件
     * @return 相对路径，原样交给前端拼成可访问地址
     */
    String put(Long userId, String scope, MultipartFile file);

    /**
     * 读一个文件。
     *
     * @param path {@link #put} 返回的相对路径
     * @throws org.springframework.core.io.ResourceNotFoundException 找不到时
     */
    Resource load(String path);
}
