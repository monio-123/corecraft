package com.mo.corecraft.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.corecraft.enums.PermissionTypeEnum;
import com.mo.corecraft.model.DateTimeFormats;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class SysPermissionResp {

    private Long id;

    private Long parentId;

    private String code;

    private String name;

    private Integer sort;

    private boolean enabled;

    private PermissionTypeEnum type;

    private String meta;

    private List<SysPermissionResp> children;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime createTime;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime updateTime;
}
