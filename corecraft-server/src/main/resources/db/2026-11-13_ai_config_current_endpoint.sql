-- ============================================================================
-- AI 配置：把「当前选中哪条 API」从浏览器搬到账号
--
-- 日期：2026-11-13
-- 表：ai_config（一人一行）
--
-- ## 为什么改
--
-- 端点列表（`ai_endpoint`）本来就是**账号级**的（带 user_id，换设备照样看到全部），
-- 但「我现在选哪一条」只存在浏览器 `localStorage['ai-current-endpoint']`。
--
-- 后果（用户 2026-11-13 报的）：在一台机器上选了 mimo，换浏览器 / 换电脑登录就掉回默认 ——
-- 而默认是「列表里第一条 ready 的」，也就是 `deepseek-flash`（id 最小）。
-- 用户看到的是「我明明配了 mimo，怎么又是 deepseek」。
--
-- ⇒ 加一列，让「选哪条」也是账号级。localStorage 降为**离线缓存**：
--    服务端有值就以它为准；服务端为空（老账号/刚上线）时认本地那份并**补写上去**。
--
-- ## 回滚
--
--   ALTER TABLE `ai_config` DROP COLUMN `current_endpoint_id`;
--
--   后端把 `AiConfig#currentEndpointId` / `AiConfigResp#currentEndpointId` /
--   `AiConfigService#useEndpoint` / `POST /ai/config/current` 一起去掉，
--   前端 `useAi.js` 的 `persistEndpoint` 与 `api/ai.js#setCurrentAiEndpoint` 一起去掉
--   （`applyEndpoint` 那套 localStorage 逻辑本来就还在，删掉推送即回到旧行为）。
--
-- ============================================================================

-- 幂等：列已存在就不动（MySQL 8 没有 ADD COLUMN IF NOT EXISTS，用动态 SQL 判一次）
SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_config'
    AND COLUMN_NAME = 'current_endpoint_id'
);
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `ai_config` ADD COLUMN `current_endpoint_id` BIGINT NULL DEFAULT NULL COMMENT ''当前选中的那条 API（一人一条）；NULL=没选过'' AFTER `enabled`',
  'SELECT ''already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ⚠️ 存量数据**不回填**：没有「原来选的是哪条」这个事实可回填
--    （旧值只存在各自浏览器里）。留 NULL ⇒ 前端第一次打开时会把本地那份补写上来，
--    这正是迁移该有的行为；回填成某个 id 反而会给用户换一条他没选过的。
