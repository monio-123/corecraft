package com.mo.corecraft.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 上传校验 + 对象键命名。**两种存储的实现都调这里，所以规则只有一份。**
 *
 * <p>键的形状是 {@code u/<userId>/<scope>/<uuid><ext>}：
 * <ul>
 *   <li>{@code u/<userId>} —— 管理维度（能按用户删 / 统计），**不是安全边界**，
 *       见 {@code FileController} 的类注释</li>
 *   <li>{@code <scope>} —— 业务（avatars / contents）</li>
 *   <li>{@code <uuid>} —— 文件名。**必须是 uuid 而不是原始文件名**：
 *       文件名可猜（{@code a.png}、{@code 1.png}），而 {@code /files/**} 免鉴权，
 *       靠的就是「猜不到」。用原始文件名等于把路径变成可枚举的</li>
 * </ul>
 *
 * <p>⚠️ **刻意没有「文件类别」这一层**（images / files / videos）：
 * 需要按类别做的事（能不能内联渲染、Content-Type 是什么）**全部能从扩展名推出来**，
 * 而让前端传就多一个能填错的字段。要真按类别分目录，
 * 判断也该在这里从扩展名推导，而不是信客户端。
 */
public final class ObjectFiles {

    /**
     * **可内联渲染**的扩展名白名单。
     *
     * <p>⚠️ **svg 不在里面，而且是故意的**：svg 里能塞 {@code <script>}，
     * 以 {@code image/svg+xml} 内联返回等于开一个存储型 XSS。
     * svg 仍然可以上传，只是会当附件下载 —— 想让它内联显示，
     * 得先在服务端把脚本剥掉，那是另一件事。
     */
    private static final Set<String> INLINE_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp");

    private ObjectFiles() {
    }

    /**
     * 上传前校验。**不限制扩展名** —— pdf / docx / mp4 都该能传，
     * 能不能内联显示是 {@link #isInlineRenderable} 在读取时决定的。
     *
     * <p>⚠️ 这里过去叫 {@code requireImage}，白名单只有图片，那是**能存图片**阶段的规则。
     * 支持附件之后如果还留着它，用户粘个 PDF 会被挡在门外。
     */
    public static void requireUploadAllowed(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("文件超过大小上限（" + (maxBytes / 1024 / 1024) + "MB）");
        }
        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            // 没有原始文件名就推不出扩展名，存进去的东西浏览器不会内联渲染。
            // 与其存一个注定不对的东西，不如当场说清楚
            throw new IllegalArgumentException("文件没有名字");
        }
    }

    /**
     * 这个路径该内联渲染还是当附件下载。
     *
     * <p>判据只有**扩展名** —— 它由 {@link #keyOf} 从原始文件名取出，
     * serve 时无法被篡改。上传时传什么字段不算数，那是用户能填错的东西。
     */
    public static boolean isInlineRenderable(String path) {
        return INLINE_EXT.contains(extOf(path));
    }

    /**
     * 对象键：{@code u/<userId>/<scope>/<uuid>/<原文件名>}。
     *
     * <p>返回相对路径而不是完整 URL —— 正文里存的是它，前端拼 {@code /api/files/}。
     * 换存储实现（本地盘 → S3）时正文一个字都不用改。
     *
     * <p>⚠️ **2026-11-16 键形状变了：uuid 升成一层目录，末段放原文件名**
     * （原来是 {@code u/<userId>/<scope>/<uuid><ext>}，原名只取扩展名、本体丢弃）。
     * 原因：**下载文件名取的是路径末段**（{@code FileController.contentDisposition}），
     * 原名不落在键里 ⇒ 用户下载到的是 {@code f9ace1ea….xlsx} 这种 UUID；
     * 界面里看到的名字只是 markdown 的**链接文字**，服务端拿不到。
     * ⇒ 键里带上原名，文件自己携带名字：直链复制给别人也是对的名字。
     *
     * <p>⚠️ **老文件不受影响**：它们的末段就是 uuid，读取时照旧（下载名仍是 uuid）。
     * 要给老文件改名得从 markdown 链接文字回填 + 迁移对象，暂不做。
     *
     * <p>⚠️ **uuid 仍然是唯一的不可猜部分**：末段是原名（用户自己看得见的），
     * 但路径的**随机段**没变 ⇒ {@code /files/**} 免鉴权的安全边界不变
     * （猜不到 uuid 就拿不到文件）。
     */
    public static String keyOf(Long userId, String scope, String originalFilename) {
        return "u/" + userId + "/" + scope + "/" + UUID.randomUUID().toString().replace("-", "")
                + "/" + safeNameOf(originalFilename);
    }

    /**
     * 原文件名 → **可放进 URL 路径的一段**。
     *
     * <p>要挡掉的字符：
     * <ul>
     *   <li>{@code / \ : * ? " < > |} —— 路径分隔符与 URL 保留字符，
     *       留着会破坏路径（{@code ?} 变查询串）</li>
     *   <li>{@code #} —— 会变成 URL 片段，后半截丢失，文件直接 404</li>
     *   <li>{@code %} —— 会跟后面两位十六进制一起被当成转义序列</li>
     *   <li>控制字符（含换行）</li>
     *   <li>{@code .} {@code ..} 单独成段 —— 那会被当成父目录</li>
     * </ul>
     * 其余（中文、空格、括号…）原样保留 —— 中文由浏览器自动 percent-encode、
     * 容器解码后原样回来。
     *
     * <p>限长 120 字符（超长截断 + 保留扩展名）—— 路径长度有上限，
     * 且文件名太长在各处都不好看。
     */
    static String safeNameOf(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename.trim();
        name = name.replaceAll("[\\\\/:*?\"<>|#%\\p{Cntrl}]", "_");
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return "file";
        }
        if (name.length() > 120) {
            // 超长：留扩展名，截主干（扩展名丢了浏览器就认不出类型）
            int dot = name.lastIndexOf('.');
            String stem = dot > 0 ? name.substring(0, dot) : name;
            String suffix = dot > 0 ? name.substring(dot) : "";
            int keep = Math.max(1, 120 - suffix.length());
            name = stem.substring(0, Math.min(stem.length(), keep)) + suffix;
        }
        return name;
    }

    /** 取小写扩展名（不含点），没有就返回空串 */
    public static String extOf(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
