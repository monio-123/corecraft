package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysDictItemCreateReq {

    @NotNull(message = "字典类型不能为空!")
    private Long dictTypeId;

    @NotBlank(message = "字典标签不能为空!")
    private String label;

    @NotBlank(message = "字典值不能为空!")
    private String value;

    private Integer sort;

    private String cssClass;

    private String remark;

    /**
     * 该类型默认是否参与掌握度：null=不配置（继承安全兜底）/ true=参与 / false=不参与。
     * 只有 {@code kp_topic_type} 那一套字典项真的消费它。
     */
    private Boolean masteryEnabled;

    private Boolean enabled;
}
