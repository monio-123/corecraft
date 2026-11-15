-- ============================================================================
-- AI 协议字典：统一显示名 + 补默认 API 地址（2026-11-12）
--
-- 背景：2026-11-11 建 `ai_protocol` 时，两项 label 是「OpenAI 兼容 / Anthropic」
--       （一个带中文后缀、一个纯英文，格式不齐），remark 存的是说明文字，
--       没有默认地址 ⇒ 设置页切换协议时「API 地址」不会跟着变。
--
-- 用户要求：① 两项命名格式统一成纯品牌名；② 每个协议对应的默认 API 地址
--           和厂商一样放进字典配置，切换协议时带出来。
--
-- 约定（**实现里的权威说明在 useAi.js / AiSettingsView.vue 注释里**，这里只记数据）：
--   - `sys_dict_type.remark`（ai_vendor_*）= 厂商默认地址（既有口径，见 AiVendorServiceImpl）
--   - `sys_dict_item.remark`（ai_protocol 的项）= **该协议的默认 API 地址**
--     `SysDictItemResp` 本来就带 remark，/dict/item/list 原样返回 ⇒ **后端零改动**
--   - label 统一纯品牌名 `OpenAI` / `Anthropic`，与 value（openai / anthropic）一一对应
--   - 地址是「基址」：后端 AiProtocol 会自己拼 /chat/completions 或 /v1/messages，
--     所以这里**不要**带具体端点路径（带 /v1 是 OpenAI 兼容各家自己的版本前缀）
--
-- ⚠️ 只 UPDATE 这两行的 label / remark；不动 value / sort / is_enabled，
--    不动任何端点数据（端点选的协议仍在 ai_endpoint.params JSON 里）。
--    WHERE 带 value + is_delete 守卫 ⇒ 可重复跑，结果一致。
--
-- ⚠️ **依赖 2026-11-11_ai_protocol_dict.sql**：本脚本没有 INSERT，
--    全新库 / 还没建过 ai_protocol 的环境要先跑那份初始化（它的 INSERT 已经
--    直接写入最终 label + remark），否则这里 0 行受影响、下拉还是空的。
--    两份脚本都不带 schema 前缀，跑的时候选中 corecraft 库。
--    线上应用与 Nacos corecraft-server-dev 连的是同一个实例同一个库
--    （106.53.205.69:3306/corecraft）⇒ **不存在第二套「线上库」要单独插**，
--    在这套库上插一次即两边同时生效。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0) 跑前自检
-- ---------------------------------------------------------------------------
SELECT i.`value`, i.`label`, i.`remark`, i.`sort` FROM `sys_dict_item` i
JOIN `sys_dict_type` t ON t.`id` = i.`dict_type_id`
WHERE t.`code` = 'ai_protocol' AND t.`is_delete` = 0 AND i.`is_delete` = 0
ORDER BY i.`sort`;


-- ---------------------------------------------------------------------------
-- 1) openai
-- ---------------------------------------------------------------------------
UPDATE `sys_dict_item` i
JOIN `sys_dict_type` t ON t.`id` = i.`dict_type_id`
SET i.`label` = 'OpenAI', i.`remark` = 'https://api.openai.com/v1'
WHERE t.`code` = 'ai_protocol' AND t.`is_delete` = 0
  AND i.`value` = 'openai' AND i.`is_delete` = 0;


-- ---------------------------------------------------------------------------
-- 2) anthropic
-- ---------------------------------------------------------------------------
UPDATE `sys_dict_item` i
JOIN `sys_dict_type` t ON t.`id` = i.`dict_type_id`
SET i.`label` = 'Anthropic', i.`remark` = 'https://api.anthropic.com'
WHERE t.`code` = 'ai_protocol' AND t.`is_delete` = 0
  AND i.`value` = 'anthropic' AND i.`is_delete` = 0;


-- ---------------------------------------------------------------------------
-- 跑后校验
-- ---------------------------------------------------------------------------
SELECT i.`value`, i.`label`, i.`remark`, i.`sort`, i.`is_enabled`
FROM `sys_dict_item` i
JOIN `sys_dict_type` t ON t.`id` = i.`dict_type_id`
WHERE t.`code` = 'ai_protocol' AND t.`is_delete` = 0 AND i.`is_delete` = 0
ORDER BY i.`sort`;
-- 期望 2 行：
--   openai    | OpenAI    | https://api.openai.com/v1
--   anthropic | Anthropic | https://api.anthropic.com

SELECT COUNT(*) AS vendor_types_unchanged FROM `sys_dict_type`
WHERE `code` LIKE 'ai_vendor\_%' AND `is_delete` = 0;
-- 期望：仍是 9（本协议字典不该被算成厂商）


-- ============================================================================
-- 回滚（恢复到 2026-11-11 初值）
-- ============================================================================
-- UPDATE `sys_dict_item` i JOIN `sys_dict_type` t ON t.`id` = i.`dict_type_id`
-- SET i.`label` = 'OpenAI 兼容', i.`remark` = '2026-11-11 预置；存量端点默认就是这个'
-- WHERE t.`code` = 'ai_protocol' AND i.`value` = 'openai' AND i.`is_delete` = 0;
-- UPDATE `sys_dict_item` i JOIN `sys_dict_type` t ON t.`id` = i.`dict_type_id`
-- SET i.`label` = 'Anthropic', i.`remark` = '2026-11-11 预置；/v1/messages + x-api-key'
-- WHERE t.`code` = 'ai_protocol' AND i.`value` = 'anthropic' AND i.`is_delete` = 0;
