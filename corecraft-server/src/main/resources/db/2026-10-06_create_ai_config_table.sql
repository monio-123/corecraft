-- ============================================================
-- AI 配置表（每人一份）
--
-- 2026-10-06 新建。长期脚本，新环境建库时也执行这一份；
-- 已有库执行同目录下的同名日期脚本即可。
--
-- 设计取舍（三个决定，改之前先看这里）：
--
-- ① **不设 provider 字段**。厂商是靠 base_url 决定的：OpenAI 兼容协议下
--    通义 / DeepSeek / Moonshot / 智谱 / OpenAI 本家全都是同一个请求格式，
--    换厂商只改 base_url 和 model，代码零改动。真碰到不兼容的厂商
--    （讯飞星火、百度那种）才需要写适配器，那是以后的事——到那时再加字段，
--    现在加就是为还不存在的需求铺路。
--
-- ② **不放 sys_dict**。字典表里没有 user_id，放进去就是全局一份，
--    和「每人一份」矛盾；而 AI 配置是**个人凭据**，跟字典里的「笔记 / 学习」
--    那种全局参考标签不是一回事。把凭据混进参考数据表，两个概念就搅在一起了。
--
-- ③ **不设 is_delete，物理删**。一个用户一行，删了下次建回来即可，
--    没有需要保留的历史。
--
-- enabled 默认 0：**没开启就不存在 AI**。界面各处用 `ai.enabled` 控制
--    AI 按钮的显示与否——不显示比"显示但点了报未配置"好，配置问题
--    不该暴露给没开启的人。
-- ============================================================

CREATE TABLE `ai_config` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL COMMENT '所属用户ID',
  `enabled`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否启用AI功能：0关闭（界面不出现任何AI入口）1开启',
  `base_url`    VARCHAR(255)          DEFAULT NULL COMMENT 'OpenAI兼容接口地址，例：https://api.deepseek.com/v1',
  `model`       VARCHAR(100)          DEFAULT NULL COMMENT '模型名，例：deepseek-chat',
  `api_key`     VARCHAR(255)          DEFAULT NULL COMMENT 'API Key。只存后端，绝不返回给前端',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  -- 一人一行。uk_user_id 已经能覆盖按 user_id 的查询，不再单建 idx_user_id（重复索引）
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI配置：每人一份，OpenAI兼容协议';