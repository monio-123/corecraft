package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 采纳 AI 归类的结果。
 *
 * <p>⚠️ **为什么采纳时还要传「目录 id」**：AI 给的是**建议**（一个目录路径），
 * 而任务跑完到人点采纳之间，目录可能变了（被删 / 改名 / 新建了同名的）。
 * ⇒ 前端在**采纳那一刻**拿着**当下的目录清单**重新解析一遍，把结果变成 id 传上来；
 * 后端再校验一次这个 id 确实是本人的目录。
 *
 * <p>两端各校验一次不是重复：前端那份是"按当下清单解析"，
 * 后端那份是"这个 id 归不归我"（前端校验不了 —— 那是服务端的知识）。
 */
@Getter
@Setter
public class KpClassifyAdoptReq {

    /** 要采纳的这些步骤（`kp_gen_step.id`） */
    @NotEmpty(message = "没有选中要采纳的条目")
    private List<Long> stepIds;

    /** 逐条的采纳内容。**按 stepId 匹配**（不靠顺序 —— 顺序错了就采纳到别的条目上去了） */
    @NotEmpty(message = "没有采纳内容")
    private List<Item> items;

    @Getter
    @Setter
    public static class Item {

        /** 对应哪个步骤 */
        @NotNull(message = "缺少步骤 id")
        private Long stepId;

        /**
         * 归到哪个目录。
         *
         * <p>⚠️ **null 有含义**：null = 留在「待归类」（用户把 AI 的建议撤了，
         * 但仍想采纳它的标题/标签）。所以这里**不能**用"没传=不改"那套判据。
         */
        private Long categoryId;

        /** 改名（null / 空 = 不改标题） */
        private String title;

        /**
         * 标签名的一批（null / 空 = 不动标签）。
         *
         * <p>⚠️ **不传 = 不动**（不是"清空"）：批量操作没人一条条看过，
         * 把用户已经打好的标签抹掉比多打几个更糟。
         */
        private List<String> tags;
    }
}
