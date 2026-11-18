package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 设置：一人一行，记录当前选中的 API 端点（{@code enabled} 总开关已于 2026-11-16 D6 退役）。
 */
@Getter
@Setter
@TableName("ai_config")
public class AiConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 当前选中的那条 API（一人一条）；NULL = 没选过，前端兜回第一条能用的。 */
    private Long currentEndpointId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}