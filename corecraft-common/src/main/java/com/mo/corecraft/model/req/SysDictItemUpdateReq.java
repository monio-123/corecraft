package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysDictItemUpdateReq {

    @NotNull(message = "id不能为空!")
    private Long id;

    private Long dictTypeId;

    private String label;

    private String value;

    private Integer sort;

    private String cssClass;

    private String remark;

    private Boolean enabled;
}
