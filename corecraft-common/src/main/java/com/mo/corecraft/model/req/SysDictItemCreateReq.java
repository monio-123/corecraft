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

    private Boolean enabled;
}
