package com.mo.corecraft.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PermissionTypeEnum {

    GROUP(0, "分组"),
    MENU(1, "菜单"),
    API(2, "接口"),
    OP(3, "操作");

    private final int code;

    private final String desc;
}
