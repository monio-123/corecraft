package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysDictTypeUpdateReq {

    @NotNull(message = "id不能为空!")
    private Long id;

    private String code;

    private String name;

    private String remark;

    private Boolean enabled;
}
