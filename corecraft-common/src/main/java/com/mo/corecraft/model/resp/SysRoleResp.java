package com.mo.corecraft.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.corecraft.model.DateTimeFormats;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class SysRoleResp {

    private Long id;

    private String code;

    private String name;

    private boolean enabled;

    private String description;

    private List<Long> permissionIds;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime createTime;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime updateTime;
}
