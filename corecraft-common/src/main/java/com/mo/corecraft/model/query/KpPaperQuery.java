package com.mo.corecraft.model.query;

import com.mo.corecraft.model.DateTimeFormats;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试历史列表筛选（设计 §11.3）：kind / 目录 / 状态 / 时间区间。
 */
@Getter
@Setter
public class KpPaperQuery {

    private Long userId;

    /** quiz / exam / review */
    private String kind;

    /**
     * 目录筛选：**一串目录 id**（前端已经把整棵子树算好了）。
     *
     * <p>口径与 {@link KpTopicQuery#getCategoryIds()} 一致：「选中一个目录 = 看整棵子树」
     * 这条规则**只有前端那一份**（store 的 getCategorySubtreeIds）——后端再实现一遍
     * 子树展开就成了两套规则，目录一改两边就对不上，**而界面上什么异常都看不到**。
     */
    private List<Long> categoryIds;

    private String status;

    /** 时间下界（含）：`yyyy-MM-dd HH:mm:ss`（{@link DateTimeFormats#DATETIME}）。空 = 不限 */
    @DateTimeFormat(pattern = DateTimeFormats.DATETIME)
    private LocalDateTime createdFrom;

    /** 时间上界（含）：同上 */
    @DateTimeFormat(pattern = DateTimeFormats.DATETIME)
    private LocalDateTime createdTo;

    /**
     * 只看「有作答还没交」的卷（最新一次 attempt 停在 answering）。
     *
     * <p>用途：考试设置面板的「有未交卷的考试」提示（TODO-exam-lifecycle §五）——
     * 目录考试的 scope 是动态筛选集合，**不按 scope 匹配**，只报个数让人去记录页翻。
     */
    private Boolean unfinished;
}
