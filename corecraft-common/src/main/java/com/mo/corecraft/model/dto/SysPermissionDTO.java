package com.mo.corecraft.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.corecraft.enums.StatusEnum;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SysPermissionDTO {

    private Long id;

    private String code;

    private String name;

    private StatusEnum status;

    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updateTime;
}
