package com.mo.corecraft.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 目录树文本的**读**与**写**（服务端唯一一份）。
 *
 * <p>为什么在服务端也要有：分段生成时，骨架、每一章的展开都是**独立的文本片段**，
 * 合并成一棵树必须先解析再重新画 —— 直接字符串拼接会让缩进/树线错位，
 * 而错位的树到了前端会被解析成另一棵树（症状是"章节跑到别的章下面"，不报错）。
 *
 * <p>格式与前端 {@code utils/outlineTree.js} 认的一致：
 * <pre>
 * JVM
 * ├── 1. 体系结构
 * │   ├── 1.1 内存区域 ｜ 划分与对象创建
 * │   └── 1.2 类加载
 * </pre>
 * 行尾用「 ｜ 」接一句定位（没有也能解析）。
 */
@Slf4j
public final class OutlineText {

    /** 树线 + 缩进（每层 4 个字符宽） */
    private static final String INDENT = "    ";

    private OutlineText() {
    }

    /**
     * 一个节点。
     *
     * @param depth  1 起
     * @param name   名称（不含定位）
     * @param intent 行尾定位，可为空串
     */
    public record Node(int depth, String name, String intent) {
    }

    /**
     * 解析目录文本。
     *
     * <p>容错优先：模型有时写 {@code ├──}，有时只写空格缩进，有时两种混着来；
     * **解析不出来就跳过这一行**（宁可少一个节点，也不要把 `├──` 当成标题）。
     */
    public static List<Node> parse(String text) {
        List<Node> out = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return out;
        }
        for (String raw : text.split("\\r?\\n")) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String line = raw.replace("\t", "    ");
            // 前导部分：空格 / │ / ├ └ 与横线；用它的宽度算层级
            int i = 0;
            while (i < line.length()) {
                char c = line.charAt(i);
                if (c == ' ' || c == '│' || c == '├' || c == '└' || c == '─' || c == '-' || c == '|') {
                    i += 1;
                } else {
                    break;
                }
            }
            String body = line.substring(i).trim();
            if (body.isEmpty()) {
                continue;
            }
            // 前导宽度 → 层级：树线里每一层是 "│   " / "    " 四格，箭头本身也是四格
            int width = i;
            int depth = width / 4 + 1;
            int sep = body.indexOf('｜');
            String name;
            String intent = "";
            if (sep < 0) {
                name = body;
            } else {
                name = body.substring(0, sep).trim();
                intent = body.substring(sep + 1).trim();
            }
            if (name.isEmpty()) {
                continue;
            }
            out.add(new Node(depth, name, intent));
        }
        return out;
    }

    /**
     * **锚定片段的第一行 = 第 1 层**（整段按它平移）。
     *
     * <p>给「逐章展开」的片段用：约定模块行顶格（第 1 行），但模型偶尔会把整段
     * 画成大树的一支——模块行自己带上 {@code ├── }（前导正好 4 格宽），按前导宽度
     * 算层级就成了第 2 层；拼树再整体 +1，这一章就沉到「节」的位置、挂到上一章
     * 最后一个节点底下（实测任务 #49 的模块 3，且 {@code verifyTree} 对这种连续
     * 深度全盲、显示「无问题」）。锚定后：无论模块行带不带树线、缩进几格，
     * 它都是片段第 1 层，整章相对层级不变。
     *
     * <p>⚠️ 只适合**单根片段**（展开片段的第一行就是模块行）；骨架解析
     * （多根、按 depth≤1 过滤）不走这个。
     *
     * <p>平移后若有行被压到第 0 层（片段后半段比第一行还浅的畸形输入），
     * 钳到第 1 层——那种片段本身就没救了，钳住至少不产生非法深度。
     */
    public static List<Node> anchorFirstLevel(List<Node> nodes) {
        if (nodes.isEmpty() || nodes.get(0).depth() == 1) {
            return nodes;
        }
        int shift = 1 - nodes.get(0).depth();
        List<Node> out = new ArrayList<>(nodes.size());
        for (Node n : nodes) {
            out.add(new Node(Math.max(n.depth() + shift, 1), n.name(), n.intent()));
        }
        return out;
    }

    /** 「N. 名字」形的章号前缀（1~2 位章号 + 点 + 空格）。
     *  「802.11 帧」「2.4GHz」这类术语名不会误中：802/2.4 后面跟的不是「点+空格」。 */
    private static final java.util.regex.Pattern CHAPTER_PREFIX =
            java.util.regex.Pattern.compile("^\\d{1,2}\\.\\s");

    /**
     * 名字是否带章号前缀（「N. 」；「N.M 」节号不带尾点，不算）。
     *
     * <p>给 {@code verifyTree} 兜底用：章号是模型自己写下的定位语义，
     * **带它的节点就该在全书第 2 层（章层）**——锚定（{@link #anchorFirstLevel}）
     * 治「模块行带树线」这一种格式抖动，序号-层级对得上对不上是语义判据：
     * 模型换一种方式把整块沉一级，锚定管不住，这里能抓（不报错级别，进 issues 给确认界面看）。
     */
    public static boolean hasChapterIndex(String name) {
        return name != null && CHAPTER_PREFIX.matcher(name).find();
    }

    /**
     * 画回树形文本。
     *
     * <p>规则：每个节点前面按**祖先链**补竖线（祖先还有后续兄弟才画 `│`），
     * 自己这一行用 `├──` / `└──`。行尾定位原样带上。
     */
    public static String render(List<Node> nodes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            if (i > 0) {
                sb.append('\n');
            }
            if (n.depth() <= 1) {
                sb.append(n.name());
            } else {
                // ⚠️ **从第 2 层开始补缩进**：根节点自己是第 1 行、不占树线。
                // 写成 `for (d = 1; ...)` 会让每个二级节点前面多出 4 个空格 ——
                // 前端的解析规则是"按前导宽度算层级"，多 4 格 = 整棵树**多出一层**，
                // 而界面上不报错，只是所有章节都往下挪了一级（很难看出来）。
                for (int d = 2; d < n.depth(); d++) {
                    sb.append(hasSiblingAfter(nodes, i, d) ? "│   " : INDENT);
                }
                sb.append(hasSiblingAfter(nodes, i, n.depth()) ? "├── " : "└── ");
                sb.append(n.name());
            }
            if (StringUtils.hasText(n.intent())) {
                sb.append(" ｜ ").append(n.intent());
            }
        }
        return sb.toString();
    }

    /**
     * 第 i 个节点之后，深度为 {@code depth} 的那一层还有没有兄弟。
     *
     * <p>这是画竖线的唯一判据：找到下一个深度 **≤ depth** 的节点，
     * 如果它正好等于 depth ⇒ 还有兄弟；更深或没有 ⇒ 这一支到底了。
     */
    private static boolean hasSiblingAfter(List<Node> nodes, int i, int depth) {
        for (int j = i + 1; j < nodes.size(); j++) {
            int d = nodes.get(j).depth();
            if (d < depth) {
                return false;
            }
            if (d == depth) {
                return true;
            }
        }
        return false;
    }

    /** 叶子（后面没有更深节点）数量 */
    public static int leafCount(List<Node> nodes) {
        int n = 0;
        for (int i = 0; i < nodes.size(); i++) {
            if (i + 1 >= nodes.size() || nodes.get(i + 1).depth() <= nodes.get(i).depth()) {
                n += 1;
            }
        }
        return n;
    }
}
