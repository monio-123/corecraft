package com.mo.corecraft.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.corecraft.model.DateTimeFormats;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SysUserDTO {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private String email;

    private String mobile;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime createTime;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime updateTime;
}
