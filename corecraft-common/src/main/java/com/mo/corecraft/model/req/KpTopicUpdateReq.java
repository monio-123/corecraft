package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KpTopicUpdateReq {

    @NotNull(message = "id不能为空!")
    private Long id;

    private String title;

    private String content;

    private Long parentTopicId;

    private Long categoryId;

    private List<String> tags;

    private List<String> quizQuestions;
}
