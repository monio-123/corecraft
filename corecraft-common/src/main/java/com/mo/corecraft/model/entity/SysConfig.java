package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统配置：通用 KV，一行一个「键 → 值」。
 *
 * **两层数据放一张表**：user_id=0 是平台级默认（管理员维护），user_id>0 是某个用户自己的覆盖值。
 * 表上 (user_id, config_key) 唯一，"系统默认每个键一行、每个用户每个键一行"由数据库兜住，
 * 代码不用再判"这条是不是已经存在"。真实用户 id 从 1 开始，不会和 0 撞。
 *
 * **值一律是字符串**：列是 varchar，"280" 是宽度还是字号由前端自己解释，后端不猜。
 *
 * **不继承 BaseEntity**：这张表没有 is_delete（配置删了再建即可），
 * create_time / update_time 交给 DDL 的 DEFAULT CURRENT_TIMESTAMP / ON UPDATE CURRENT_TIMESTAMP。
 */
@Getter
@Setter
@TableName("sys_config")
public class SysConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 0 = 平台级默认（不属于任何用户）；>0 = 该用户自己的覆盖值 */
    @TableField("user_id")
    private Long userId;

    /** 配置键，格式「命名空间.键名」，例 ui.sideWidth。命名空间用来分域，前端按前缀取 */
    @TableField("config_key")
    private String configKey;

    /** 配置值，一律是字符串 */
    @TableField("config_value")
    private String configValue;

    /** 说明（给人看的）。只有系统默认那几行用得上 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
