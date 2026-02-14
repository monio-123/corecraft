package com.mo.corecraft.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.corecraft.enums.PermissionTypeEnum;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SysPermissionResp {

    private Long id;

    private String code;

    private String name;

    private Integer sort;

    private boolean enabled;

    private PermissionTypeEnum type;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updateTime;
}
