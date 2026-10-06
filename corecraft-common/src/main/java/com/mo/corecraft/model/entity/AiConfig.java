package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 设置：一人一行，**只有一个总开关**。
 *
 * **接口不在这里**：base_url / model / api_key 拆到了 {@link AiEndpoint}（一人多行）。
 * 开关是用户级的（要不要用 AI），接口是配置级的（用哪个），一个字段一个归属，
 * 免得"多行 enabled 各不相同"这种事。
 *
 * **不放 sys_dict**：字典表没有 user_id，放进去就是全局一份，和"每人一份"矛盾。
 *
 * **不继承 BaseEntity**：这里不需要 is_delete（一行配置，删了再建即可），
 * createTime / updateTime 交给数据库的 DEFAULT CURRENT_TIMESTAMP。
 */
@Getter
@Setter
@TableName("ai_config")
public class AiConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 0 关闭（界面不出现任何 AI 入口）1 开启 */
    private Boolean enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}