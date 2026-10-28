package com.mo.corecraft.model.query;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

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
}
