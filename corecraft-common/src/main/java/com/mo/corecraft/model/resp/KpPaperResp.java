package com.mo.corecraft.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.corecraft.model.DateTimeFormats;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 试卷详情（含题目，答题/回看用）。**标准答案属用户私有数据**，接口一律校验 user_id。
 */
@Getter
@Setter
public class KpPaperResp {

    private Long id;

    /** quiz / exam / review */
    private String kind;

    private Long categoryId;

    private String title;

    /** generating / ready / failed */
    private String status;

    /** 当时范围快照（知识点 id、标题、内容 hash/epoch、目录路径） */
    private Map<String, Object> scopeSnapshot;

    /** 出卷参数快照 */
    private Map<String, Object> settingsSnapshot;

    private List<KpQuestionResp> questions;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime createTime;
}
