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
     * 对象键：{@code u/<userId>/<scope>/<uuid><ext>}。
     *
     * <p>返回相对路径而不是完整 URL —— 正文里存的是它，前端拼 {@code /api/files/}。
     * 换存储实现（本地盘 → S3）时正文一个字都不用改。
     */
    public static String keyOf(Long userId, String scope, String originalFilename) {
        String suffix = suffixOf(originalFilename);
        return "u/" + userId + "/" + scope + "/" + UUID.randomUUID().toString().replace("-", "") + suffix;
    }

    /** 取小写扩展名（不含点），没有就返回空串 */
    public static String extOf(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 扩展名从**原始文件名**上取，不用 {@code getContentType()} 反查 ——
     * 两者对不上时（把 .png 改名成 .jpg 再传），浏览器给的 type 才是不可信的。
     *
     * <p>取不到就留空：没有扩展名的对象照样存得进去，只是浏览器不内联渲染。
     */
    private static String suffixOf(String originalFilename) {
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        String suffix = originalFilename.substring(dot);
        // 只留字母数字和点，`..` / `/` 这类不许进来
        return suffix.matches("\\.[A-Za-z0-9]{1,10}") ? suffix.toLowerCase(Locale.ROOT) : "";
    }
}
