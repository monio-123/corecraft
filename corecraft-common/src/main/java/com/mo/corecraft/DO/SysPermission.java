package com.mo.corecraft.DO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysPermission extends BaseDO {

    private String code;

    private String name;

    private String type;

    private String url;

    private String method;

    private Long parentId;

    private Integer sort;
}
