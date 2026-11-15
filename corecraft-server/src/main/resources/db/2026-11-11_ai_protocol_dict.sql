-- ============================================================================
-- AI 端点「协议类型」进字典（2026-11-11）
--
-- 目的：AI 设置页新增「协议类型」下拉（openai / anthropic），选项不写死在前端，
--       和厂商一样由字典维护 ⇒ 管理员在「系统管理 → 字典」里就能加第三种协议。
--
-- 本脚本只做一件事：新增 1 个字典类型 + 2 个字典项。
--   - **不动表结构**：端点选的协议存在 `ai_endpoint.params` 这个 JSON 列里
--     （和 vendor / custom 并列，键名 `protocol`），存量行没这个键 ⇒ 一律按 openai。
--   - **不改存量数据**：现有端点全是 OpenAI 兼容地址，默认协议就是 openai。
--   - **字典项的 remark = 该协议的默认 API 地址**（和厂商字典类型 remark 存地址同一个
--     口径）：前端切协议时把它带进「API 地址」输入框。label 统一纯品牌名，
--     与 value 一一对应。见 2026-11-12_ai_protocol_defaults.sql。
--   - code 取 `ai_protocol`，**不带 `ai_vendor_` 前缀** ⇒ 不会被
--     `AiVendorServiceImpl.list()` 当成厂商列进下拉，也不会被厂商脚本的
--     「厂商类型 9 个」校验算进去。
--
-- ⚠️ 每条 INSERT 自带守卫：全新建库时本脚本跟在 `knowledge_platform.sql` 后面跑，
--    重跑一次不能撞 `uk_sys_dict_type_code` / `uk_sys_dict_item_type_value`。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0) 跑前自检：类型在不在、项有几条
-- ---------------------------------------------------------------------------
SELECT `id`, `code`, `name` FROM `sys_dict_type`
WHERE `code` = 'ai_protocol' AND `is_delete` = 0;

SELECT i.`value`, i.`label`, i.`sort`, i.`is_enabled`
FROM `sys_dict_item` i
JOIN `sys_dict_type` t ON t.`id` = i.`dict_type_id`
WHERE t.`code` = 'ai_protocol' AND i.`is_delete` = 0
ORDER BY i.`sort`;


-- ---------------------------------------------------------------------------
-- 1) 字典类型
-- ---------------------------------------------------------------------------
INSERT INTO `sys_dict_type` (`code`, `name`, `remark`, `is_enabled`)
SELECT 'ai_protocol', 'AI 协议类型',
       '端点调用协议：openai=OpenAI 兼容 /chat/completions；anthropic=Anthropic /v1/messages', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `code` = 'ai_protocol');


-- ---------------------------------------------------------------------------
-- 2) 字典项：value = 协议 code，label = 下拉显示名（统一纯品牌名），
--            remark = 该协议默认 API 地址（切协议时前端带出）
-- ---------------------------------------------------------------------------
INSERT INTO `sys_dict_item` (`dict_type_id`, `label`, `value`, `sort`, `remark`)
SELECT t.`id`, 'OpenAI', 'openai', 1, 'https://api.openai.com/v1'
FROM `sys_dict_type` t
WHERE t.`code` = 'ai_protocol' AND t.`is_delete` = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_dict_item` i
                  WHERE i.`dict_type_id` = t.`id` AND i.`value` = 'openai');

INSERT INTO `sys_dict_item` (`dict_type_id`, `label`, `value`, `sort`, `remark`)
SELECT t.`id`, 'Anthropic', 'anthropic', 2, 'https://api.anthropic.com'
FROM `sys_dict_type` t
WHERE t.`code` = 'ai_protocol' AND t.`is_delete` = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_dict_item` i
                  WHERE i.`dict_type_id` = t.`id` AND i.`value` = 'anthropic');


-- ---------------------------------------------------------------------------
-- 跑后校验
-- ---------------------------------------------------------------------------
SELECT COUNT(*) AS protocol_types FROM `sys_dict_type`
WHERE `code` = 'ai_protocol' AND `is_delete` = 0;
-- 期望：1

SELECT i.`value`, i.`label`, i.`remark`, i.`sort` FROM `sys_dict_item` i
JOIN `sys_dict_type` t ON t.`id` = i.`dict_type_id`
WHERE t.`code` = 'ai_protocol' AND i.`is_delete` = 0
ORDER BY i.`sort`;
-- 期望：2 行
--   openai    | OpenAI    | https://api.openai.com/v1
--   anthropic | Anthropic | https://api.anthropic.com

SELECT COUNT(*) AS vendor_types_unchanged FROM `sys_dict_type`
WHERE `code` LIKE 'ai_vendor\_%' AND `is_delete` = 0;
-- 期望：仍是 9（本协议类型不该被算成厂商）


-- ============================================================================
-- 回滚
-- ============================================================================
-- R1) 先删项再删类型（外键/引用顺序）
-- DELETE i FROM `sys_dict_item` i
-- JOIN `sys_dict_type` t ON t.id = i.dict_type_id
-- WHERE t.code = 'ai_protocol' AND i.value IN ('openai', 'anthropic');
-- R2) DELETE FROM `sys_dict_type` WHERE `code` = 'ai_protocol';
