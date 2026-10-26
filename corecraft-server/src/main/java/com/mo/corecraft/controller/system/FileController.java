package com.mo.corecraft.controller.system;

import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.storage.FileStorage;
import com.mo.corecraft.storage.ObjectFiles;
import com.mo.corecraft.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * 文件上传与读取。
 *
 * <p>URL 形状是 {@code /api/files/u/<userId>/<scope>/<name>}（{@code context-path} 是 {@code /api}）。
 * 正文里存的就是这个相对路径的 markdown 链接。
 *
 * <p>⚠️ **路径里的 {@code u/<userId>} 是管理维度，不是安全边界。**
 * {@code /files/**} 免鉴权（{@code <img src>} 不带 Authorization 头），
 * 所以 serve 时**拿不到"当前用户"，无从校验路径里的 userId 是谁的**。
 * 真正挡人的仍然只有文件名里的 UUID 不可猜。分目录换来的是
 * 「能按用户删 / 能按用户统计 / 哪天 UUID 生成被改坏了爆炸半径限制在一个用户目录内」。
 */
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    /**
     * 允许的业务分区。白名单，不是"传什么就用什么" —— scope 决定文件落在哪个目录。
     *
     * <p>⚠️ **只放「业务」，不放「文件类别」**：类别（图片/文件/视频）
     * 应该由 {@code ObjectFiles} 从扩展名推导 —— 前端能传的参数就是能填错的参数，
     * 而「路径说 files/ 里放了个 .png」这种两份真相正是这个项目反复踩的坑。
     *
     * <p>{@code contents} 对应 {@code kp_topic.content}（正文），
     * 和 {@code avatars} 一样是领域名词，不是比喻。
     */
    private static final Set<String> SCOPES = Set.of("avatars", "contents");

    private static final String PREFIX = "/files/";

    private final FileStorage fileStorage;

    @PostMapping("/upload")
    public ResultResp<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                                  @RequestParam("scope") String scope) {
        if (!SCOPES.contains(scope)) {
            throw new IllegalArgumentException("不支持的文件分区");
        }
        Long userId = SecurityUtil.getUserId();
        return ResultResp.data(Map.of("path", fileStorage.put(userId, scope, file)));
    }

    /**
     * 读取文件。
     *
     * <p>⚠️ **这条路径是免鉴权的**（见 {@code ResourceServerConfig} 的 permitAll），
     * 因为 {@code <img src>} 由浏览器直接发起、**不会带上 Authorization 头**
     * （JWT 存在 localStorage，只有 axios 会手动加 header）。加了鉴权图片就是一片裂图。
     *
     * <p>所以安全性靠的是**文件名不可猜**（UUID），不是靠权限码，也不是靠路径里的 userId。
     * 要更严就得改走预签名 URL 或给文件名加服务端签名，那是另一套东西。
     */
    @GetMapping("/**")
    public ResponseEntity<Resource> serve(HttpServletRequest request) {
        String path = relativePath(request);
        Resource resource = fileStorage.load(path);
        boolean inline = ObjectFiles.isInlineRenderable(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                // 图片 inline（要在浏览器里显示），附件 attachment（浏览器直接下载）。
                // ⚠️ **判据是扩展名而不是上传时传的字段**：传上来的字段是用户能填错的东西，
                // 而扩展名由 ObjectFiles 从原始文件名取出、且 serve 时无法被篡改。
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(path, inline))
                .contentType(mediaTypeOf(path))
                .body(resource);
    }

    /**
     * 取出 {@code /files/} 后面的相对路径。
     *
     * <p>用 {@code getServletPath()}（已去掉 context-path）而不是 {@code getRequestURI()}
     * ——后者带的是 {@code /api/files/...}，前缀对不上。
     */
    private String relativePath(HttpServletRequest request) {
        String path = request.getServletPath();
        int i = path.indexOf(PREFIX);
        return i >= 0 ? path.substring(i + PREFIX.length()) : path;
    }

    /**
     * **可内联渲染的扩展名白名单**。
     *
     * <p>⚠️ **svg 不在里面，而且是故意的**：svg 里能塞 {@code <script>}，
     * 以 {@code image/svg+xml} 内联返回就等于开了个存储型 XSS。
     * svg 仍然可以上传，只是会以 attachment 下载 —— 想要它内联显示，
     * 得先在服务端把脚本剥掉，那是另一件事。
     */
    private MediaType mediaTypeOf(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        // ⚠️ **必须回落到 octet-stream 而不是猜**：Content-Type 猜错时
        // 浏览器会把任意文件当可执行内容解析
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /**
     * 内联还是下载。
     *
     * <p>inline 给文件名会被浏览器当标题弹出来（UUID 一串随机字符），
     * 而 attachment 的文件名是 UUID 更没意义 —— 所以两边都只带
     * {@code filename*}，让浏览器至少知道这是什么类型的东西。
     */
    private String contentDisposition(String path, boolean inline) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return (inline ? "inline" : "attachment") + "; filename*=UTF-8''"
                + URLEncoder.encode(name, StandardCharsets.UTF_8);
    }
}
