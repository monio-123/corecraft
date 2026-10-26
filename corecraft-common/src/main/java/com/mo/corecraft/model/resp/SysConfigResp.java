package com.mo.corecraft.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 管理端看到的配置行。
 *
 * **带 userId**：管理端一眼要分得清"这行是系统默认"（user_id=0）还是"某个用户的覆盖"。
 */
@Getter
@Setter
public class SysConfigResp {

    private Long id;

    /** 0 = 系统默认；>0 = 某个用户自己的覆盖值 */
    private Long userId;

    private String key;

    private String value;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updateTime;
}
