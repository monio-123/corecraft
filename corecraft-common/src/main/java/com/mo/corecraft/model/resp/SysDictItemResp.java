package com.mo.corecraft.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.corecraft.model.DateTimeFormats;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SysDictItemResp {

    private Long id;

    private Long dictTypeId;

    private String label;

    private String value;

    private Integer sort;

    private String cssClass;

    private String remark;

    /**
     * 该类型默认是否参与掌握度：NULL=未配置 / TRUE=参与 / FALSE=不参与。
     * 只有 {@code kp_topic_type} 那一套字典项真的消费它（见 SysDictItem.masteryEnabled）。
     */
    private Boolean masteryEnabled;

    private boolean enabled;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime createTime;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime updateTime;
}
