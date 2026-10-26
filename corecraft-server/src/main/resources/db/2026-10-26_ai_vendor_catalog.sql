-- ============================================================================
-- AI 接入设置改造（2026-10-26）
--
-- 目的：配 AI 时不用再自己去官网翻"接口地址 + 有哪些模型"——
--      厂商和模型清单进字典，界面上直接选；还能一键从厂商接口拉最新模型清单。
--
-- 本脚本做四件事：
--   1) ai_endpoint 加一列 params：{"vendor":"deepseek","custom":{...}}
--      （vendor = 厂商 code；custom = 自定义参数，展开进 chat 请求体）
--      —— **幂等**（列已经有了就跳过）；⚠️ 全新建库的话 `knowledge_platform.sql`
--      里已经带这列了，表结构只存那一份。
--   2) 厂商 = 一个字典类型，code 前缀 ai_vendor_，类型 remark = 该厂商的
--      OpenAI 兼容地址（这是「厂商 → 模型」这套层级在字典里的落法：
--      厂商放「类型」那层、模型放「字典项」那层 —— 字典表本来就是
--      「类型 + 平铺的项」两层，项下没有子项，所以厂商只能占类型那层）
--   3) DeepSeek 的两个模型项预置（其余厂商的模型清单靠界面上的「刷新」拉）
--   4) 存量端点回填 vendor
--
-- 跑前自检 / 跑后校验 / 回滚段都在下面。
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 0) 跑前自检
-- ---------------------------------------------------------------------------
-- 0.1 确认 ai_endpoint 的 params 列在不在
SELECT COUNT(*) AS params_col_exists
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'ai_endpoint' AND column_name = 'params';
-- 期望：1（=1 表示列已经有了，第 1 段会跳过；=0 表示要给老库补列）

-- 0.2 记下当前端点（回滚要靠它对账）
SELECT id, user_id, name, base_url, model FROM ai_endpoint ORDER BY id;


-- ---------------------------------------------------------------------------
-- 1) ai_endpoint 加一列 params（**幂等**：列已经有了就跳过）
-- ---------------------------------------------------------------------------
-- **为什么只加一列**：厂商和模型从字典来（不用存）；
--   base_url / model 表里本来就有（用户可以覆盖字典里的默认值 —— 中转地址是真实需求）；
--   api_key 必须每人一份留在表里（字典是全局的，密钥放全局 = 所有人的密钥堆一起）；
--   ⇒ 真正新增的只有「自定义参数」这一份每人不同的东西。
--
-- ⚠️ **全新建库不用跑这一段** —— `knowledge_platform.sql` 里已经带这列了
--    （表结构只有一份，那边是权威）。这一段是给"2026-10-26 之前就部署过的库"补列用的。
--    MySQL 的 ADD COLUMN 没有 IF NOT EXISTS ⇒ 用 information_schema 判断一次。
SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_endpoint' AND column_name = 'params') > 0,
  'SELECT ''params 列已存在，跳过'' AS notice',
  'ALTER TABLE `ai_endpoint` ADD COLUMN `params` text DEFAULT NULL COMMENT ''端点附加参数 JSON：{"vendor":"厂商code","custom":{...}}。custom 展开进 chat 请求体（不能覆盖 model/stream/messages）'' AFTER `model`');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------------
-- 2) 厂商 = 字典类型
-- ---------------------------------------------------------------------------
-- **code 前缀 ai_vendor_ 是这套机制的约定**：
--   - 前端按前缀列出"有哪些厂商"
--   - 厂商 code（deepseek）↔ 类型 code（ai_vendor_deepseek）一一对应
--     ⇒ 刷新模型时才知道该把模型写进哪个字典类型
--   - remark 存该厂商的 OpenAI 兼容地址（选厂商时自动带出，用户可改）
--
-- 地址来源（2026-10-26 核过官方文档 / 多来源一致）：
--   DeepSeek   api-docs.deepseek.com/api/list-models  → GET /models
--   百炼       help.aliyun.com（OpenAI兼容-Chat）      → 旧域名仍可用
--   智谱       open.bigmodel.cn/api/paas/v4
--   Kimi       api.moonshot.cn/v1
--   MiniMax    api.minimax.chat/v1（国际站是 api.minimaxi.com）
--   OpenAI     api.openai.com/v1
--   硅基流动   api.siliconflow.cn/v1
--   火山方舟   ark.cn-beijing.volces.com/api/v3
--
-- ⚠️ **只有 DeepSeek 官方明确有 GET /models**；别的厂商的 OpenAI 兼容
--    能不能列模型没逐个核过 ⇒ 「刷新」失败时不要清空已有清单（代码里就是这么做的），
--    报错会带上游原文，自己判断是"厂商没这个接口"还是"key 不对"。
INSERT INTO `sys_dict_type` (`code`, `name`, `remark`, `is_enabled`) VALUES
('ai_vendor_deepseek',    'DeepSeek',              'https://api.deepseek.com',                    1),
('ai_vendor_dashscope',  '阿里百炼（通义千问）',  'https://dashscope.aliyuncs.com/compatible-mode/v1', 1),
('ai_vendor_zhipu',      '智谱 GLM',              'https://open.bigmodel.cn/api/paas/v4',          1),
('ai_vendor_moonshot',   'Kimi（Moonshot）',      'https://api.moonshot.cn/v1',                   1),
('ai_vendor_minimax',    'MiniMax',               'https://api.minimax.chat/v1',                  1),
('ai_vendor_openai',     'OpenAI',                'https://api.openai.com/v1',                    1),
('ai_vendor_siliconflow','硅基流动 SiliconFlow',  'https://api.siliconflow.cn/v1',                 1),
('ai_vendor_volcengine', '火山方舟（豆包）',      'https://ark.cn-beijing.volces.com/api/v3',     1);


-- ---------------------------------------------------------------------------
-- 3) DeepSeek 的模型项预置
-- ---------------------------------------------------------------------------
-- label = 显示名（厂商接口返回的 name），value = **模型 id**（真正发给 API 的那个串）。
-- 其余厂商的模型清单点「刷新」自动灌进来，这里不预置（避免预置一堆过期的）。
INSERT INTO `sys_dict_item` (`dict_type_id`, `label`, `value`, `sort`, `remark`)
SELECT id, 'DeepSeek-V4.1-Flash', 'deepseek-flash', 1, '2026-10-26 预置'
FROM `sys_dict_type` WHERE `code` = 'ai_vendor_deepseek' AND `is_delete` = 0;

INSERT INTO `sys_dict_item` (`dict_type_id`, `label`, `value`, `sort`, `remark`)
SELECT id, 'DeepSeek-V4-Pro', 'deepseek-v4-pro', 2, '2026-10-26 预置'
FROM `sys_dict_type` WHERE `code` = 'ai_vendor_deepseek' AND `is_delete` = 0;


-- ---------------------------------------------------------------------------
-- 4) 存量端点回填 vendor
-- ---------------------------------------------------------------------------
-- **按 id 一条条写，不按 base_url 匹配**：
--   端点 5 用的是百炼的业务空间专属域名（maas.qianwenaiapi.com），
--   按地址匹配认不出来 ⇒ 只能显式指定。
UPDATE `ai_endpoint` SET `params` = '{"vendor":"deepseek"}' WHERE `id` IN (1, 3);
UPDATE `ai_endpoint` SET `params` = '{"vendor":"dashscope"}' WHERE `id` = 5;


-- ---------------------------------------------------------------------------
-- 跑后校验
-- ---------------------------------------------------------------------------
-- 4.1 列加上了
SELECT COUNT(*) AS params_col_exists
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'ai_endpoint' AND column_name = 'params';
-- 期望：1

-- 4.2 厂商类型 8 个
SELECT COUNT(*) AS vendor_types FROM `sys_dict_type`
WHERE `code` LIKE 'ai_vendor\_%' AND `is_delete` = 0;
-- 期望：8

-- 4.3 DeepSeek 型号 2 个
SELECT COUNT(*) AS deepseek_models FROM `sys_dict_item` i
JOIN `sys_dict_type` t ON t.id = i.dict_type_id
WHERE t.code = 'ai_vendor_deepseek' AND i.is_delete = 0;
-- 期望：2

-- 4.4 端点都认到厂商了
SELECT id, name, model, params FROM `ai_endpoint` ORDER BY id;
-- 期望：3 行，params 分别是 {"vendor":"deepseek"} / {"vendor":"deepseek"} / {"vendor":"dashscope"}


-- ============================================================================
-- 回滚
-- ============================================================================
-- R1) 端点上的 vendor 标记清掉（只清本脚本加的那段，custom 不动）
UPDATE `ai_endpoint` SET `params` = NULL
WHERE `id` IN (1, 3) AND `params` = '{"vendor":"deepseek"}';
UPDATE `ai_endpoint` SET `params` = NULL
WHERE `id` = 5 AND `params` = '{"vendor":"dashscope"}';

-- R2) 预置的 DeepSeek 型号删掉
DELETE i FROM `sys_dict_item` i
JOIN `sys_dict_type` t ON t.id = i.dict_type_id
WHERE t.code = 'ai_vendor_deepseek'
  AND i.value IN ('deepseek-flash', 'deepseek-v4-pro')
  AND i.remark = '2026-10-26 预置';

-- R3) 厂商类型删掉（先删项再删类型，见 R2）
DELETE FROM `sys_dict_type` WHERE `code` LIKE 'ai_vendor\_%' AND `remark` LIKE 'http%';

-- R4) 列去掉
-- ⚠️ **只有"这段确实加过列"时才 drop**：全新建库的列由 `knowledge_platform.sql` 管，
--    drop 了新库就少这一列（代码一查就 Unknown column）。
ALTER TABLE `ai_endpoint` DROP COLUMN `params`;
