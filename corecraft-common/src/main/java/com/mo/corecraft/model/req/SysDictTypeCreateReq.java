package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysDictTypeCreateReq {

    @NotBlank(message = "字典编码不能为空!")
    private String code;

    @NotBlank(message = "字典名称不能为空!")
    private String name;

    private String remark;

    private Boolean enabled;
}
