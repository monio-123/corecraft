package com.mo.corecraft.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.corecraft.model.DateTimeFormats;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SysDictTypeResp {

    private Long id;

    private String code;

    private String name;

    private String remark;

    private boolean enabled;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime createTime;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime updateTime;
}
