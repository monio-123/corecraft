package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KpTopicCreateReq {

    private String title;

    private String content;

    private Long parentTopicId;

    private Long treeId;

    private List<String> tags;

    private List<String> quizQuestions;
}
