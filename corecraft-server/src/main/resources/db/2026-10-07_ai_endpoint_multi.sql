-- ============================================================
-- AI：一人一行 → 一人多接口（2026-10-07）
--
-- ## 为什么拆两张表
--
-- 原设计是 `ai_config` 一人一行（UNIQUE user_id），装下一个 base_url + model + api_key。
-- 用户要"看到自己加了哪些 api、能切换着用多个模型"，一行装不下。
--
-- 为什么不选另外两条路：
--
-- ① **一表多行**（给 ai_config 加 name，放开 uk_user_id）：少一张表，但 `enabled` 是
--    **用户级**开关，它存哪？只能约定"每行都存一份、保存时全表更新"——靠约定维持
--    一致性，多条记录时任何一条走错路径就会出现"有的开有的关"。
-- ② **单行加 endpoints JSON**：表结构一行不改，但 api_key 全塞进 JSON 后，前端又不能
--    回传 key（密钥只进不出），每次保存都得按 id 把服务端的旧 key 合并回去——多一层
--    容易写错的逻辑，而且 JSON 列没法按字段审计。
--
-- 拆开之后每个字段只有一个归属：开关归 ai_config，接口归 ai_endpoint。
--
-- ## 「当前选中哪个接口」不落库
--
-- 存前端 localStorage。存后端要多一列 + 一次更新，换来的只是"多设备同步一个下拉选择"，
-- 而这是个单人知识库，选哪个接口是当下的临时选择，不是用户资料。
--
-- ## enabled 默认 0
--
-- 没开启就不出现任何 AI 入口，和原来一致。
-- ============================================================

CREATE TABLE `ai_endpoint` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL COMMENT '所属用户ID',
  `name`        VARCHAR(64)  NOT NULL COMMENT '自己起的名字，列表里靠它区分',
  `base_url`    VARCHAR(255)          DEFAULT NULL COMMENT 'OpenAI兼容接口地址，例：https://api.deepseek.com/v1',
  `model`       VARCHAR(100)          DEFAULT NULL COMMENT '模型名，例：deepseek-chat',
  `api_key`     VARCHAR(255)          DEFAULT NULL COMMENT 'API Key。只存后端，绝不返回给前端',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI接口：每人多个，提问时选一个用';

-- ---------- 迁移已有数据 ----------
-- 1) 老 ai_config 里那一行搬进 ai_endpoint，别让用户重新填一遍 key
INSERT INTO `ai_endpoint` (`user_id`, `name`, `base_url`, `model`, `api_key`)
SELECT `user_id`, '默认', `base_url`, `model`, `api_key`
FROM `ai_config`
WHERE `base_url` IS NOT NULL;

-- 2) ai_config 只留开关。三列先搬走再删
ALTER TABLE `ai_config` DROP COLUMN `base_url`;
ALTER TABLE `ai_config` DROP COLUMN `model`;
ALTER TABLE `ai_config` DROP COLUMN `api_key`;
ALTER TABLE `ai_config` COMMENT 'AI设置：每人一行，只管开关。接口在 ai_endpoint';