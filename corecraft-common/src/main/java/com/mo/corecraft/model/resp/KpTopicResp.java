package com.mo.corecraft.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class KpTopicResp {

    private Long id;

    private String title;

    private String content;

    private Long parentTopicId;

    /** 所属目录ID（null = 未归类）；目录名由前端 store 从目录列表解析 */
    private Long categoryId;

    private String tags;

    private String quizQuestions;

    private List<Long> relatedTopicIds;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updateTime;
}
