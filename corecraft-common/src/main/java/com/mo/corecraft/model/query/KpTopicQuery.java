package com.mo.corecraft.model.query;

import com.mo.corecraft.model.DateTimeFormats;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class KpTopicQuery {

    private Long id;

    private Long userId;

    /**
     * 目录筛选：**一串目录 id**（前端已经把整棵子树算好了）。
     *
     * <p>为什么不让后端按一个 categoryId 自己算子树：「选中一个目录 = 看整棵子树」
     * 这条规则**只有前端那一份**（store 的 getCategorySubtreeIds）。列表的分面计数、
     * 考试的取值范围都按它算。后端再实现一遍就成了两套规则 —— 目录一改，
     * 列表看到的和考的就对不上，**而界面上什么异常都看不到**。
     */
    private List<Long> categoryIds;

    /**
     * 只看「待归类」（category_id 为空）。
     *
     * <p>与 categoryIds **互斥**：前端两个只发一个。后端两个都收到时以本项为准
     * （未归类是更窄的条件，先收窄再谈目录）。
     */
    private Boolean uncategorized;

    /** 知识点类型（字典项 id） */
    private Long typeId;

    /** 标签名 */
    private String tag;

    /**
     * 时间的下界（**含**）：`yyyy-MM-dd HH:mm:ss`。空 = 不限。
     *
     * <p>**为什么是本地墙钟时间（不带时区）**：前端按**本地时区**算好起止再传过来
     * （"今天" = 本地 00:00:00 起），库里 {@code create_time} 存的也是本地墙钟时间
     * ⇒ 两端比的是同一个东西，**不会因为时区换算差一天**。
     *
     * <p>⚠️ **分面计数走的是结构索引，不是这个参数** —— 索引那条 SQL 不吃时间筛选，
     * 前端在索引那份数据上自己按 {@code createTime} 筛。两边口径必须一样
     * （前端那个算出来的起止就是这里收的同一对值）⇒ 否则"下拉里的数字"和
     * "点进去看到的条数"会漂，而**界面上什么异常都看不到**。
     */
    @DateTimeFormat(pattern = DateTimeFormats.DATETIME)
    private LocalDateTime createdFrom;

    /** 时间的上界（含）：`yyyy-MM-dd HH:mm:ss`。空 = 不限。 */
    @DateTimeFormat(pattern = DateTimeFormats.DATETIME)
    private LocalDateTime createdTo;

    /**
     * 关键词：标题和正文都搜。
     *
     * <p>**空白在这里归一**（trim + 空串归 null）——**只有这一处**。
     * 索引那条 SQL（手写的 KpTopicMapper.xml）和列表那条（MP 的 wrapper）
     * 用的是**同一个字段**，两边归一口径不一样的话：
     * " mysql " 在索引里按 %mysql% 命中、在列表里按 % mysql % 不命中
     * ⇒ **两个接口给出的条数对不上，而界面上什么异常都看不到**
     * （前端拿索引算分面计数、拿列表取当页条数，数字一漂就是"写着 3、点进去 2"）。
     */
    @Setter(AccessLevel.NONE)
    private String keyword;

    /**
     * 关键词归一：**trim + 空串归 null**。**只有这一份实现**，
     * setKeyword 和索引那条（KpTopicRepository#listIndex）都走它。
     */
    public static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public void setKeyword(String keyword) {
        this.keyword = normalizeKeyword(keyword);
    }

    // ==================== 关键词搜哪一块 ====================

    /** 标题和正文都搜（**默认**）。前端「搜正文」那档就是它（用户 2026-11-05：
     *  「对于卡片栏搜索模式 搜索的包括正文和正文标题」）。 */
    public static final String SCOPE_BOTH = "both";
    /** **只搜正文**。⚠️ **前端当前没有哪一档用它**（「正文」那档是 {@link #SCOPE_BOTH}，
     *  「目录」那档是 {@link #SCOPE_TITLE}）⇒ 它是**对外契约面上留着的一档**，
     *  不是死代码 —— 认它的人（别的接入方）传它就按只搜正文算。 */
    public static final String SCOPE_CONTENT = "content";
    /** **只搜标题**（「搜目录」那档 —— 目录名在前端本地匹配，知识点标题由这一档负责）。 */
    public static final String SCOPE_TITLE = "title";

    /**
     * 关键词搜哪一块。
     *
     * <p>⚠️ **默认必须是 {@link #SCOPE_BOTH}**：这个字段不传时（老调用方 / 别的接口）
     * 行为不能变 —— 「标题 + 正文都搜」是原来那条语义。
     */
    @Setter(AccessLevel.NONE)
    private String scope = SCOPE_BOTH;

    /**
     * scope 归一：**认不出来的按 {@link #SCOPE_BOTH} 算** —— 一个手改坏的参数
     * 不该把搜索搞空（和 {@link #rangeFromRoute} 那条一个道理：坏参数按默认算）。
     */
    public static String normalizeScope(String scope) {
        if (scope == null) {
            return SCOPE_BOTH;
        }
        String trimmed = scope.trim();
        if (SCOPE_CONTENT.equals(trimmed) || SCOPE_TITLE.equals(trimmed)) {
            return trimmed;
        }
        return SCOPE_BOTH;
    }

    public void setScope(String scope) {
        this.scope = normalizeScope(scope);
    }
}
