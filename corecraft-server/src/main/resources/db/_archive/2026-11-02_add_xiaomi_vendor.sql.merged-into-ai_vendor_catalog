-- ============================================================================
-- 新增「小米 MiMo」厂商（2026-11-02）
--
-- 目的：AI 接入设置里能选到小米的 MiMo（小米自研，OpenAI 兼容协议）。
--
-- 为什么是**新增厂商**而不是复用现有的（2026-11-02 读过代码才定的）：
--   厂商 = 一个字典类型（code 前缀 ai_vendor_，**类型 remark = 该厂商的
--   OpenAI 兼容地址**），模型 = 那个类型下的字典项。
--   ⚠️ `AiVendorServiceImpl.refresh()` 的 `syncModels()` 把拉回来的清单
--      **写回"用户选中的那个厂商"** —— 于是选「OpenAI」再手填小米地址去点
--      「更新」，小米的模型会写进 OpenAI 那一组（字典是**全局**的，影响所有人），
--      而且「这轮没返回的 = 停用」会把 OpenAI 原有的模型批量停用。
--      ⇒ **模型清单是厂商级的东西，地址必须归它自己那个厂商**。
--      （功能上复用也能跑通 —— 模型下拉是 allow-create 能手填 —— 但那是拿
--        别人的厂商组当临时口袋用。）
--
-- 地址来源（2026-11-02 核官方文档 platform.xiaomimimo.com/docs）：
--   小米 MiMo 开放平台**兼容 OpenAI API 与 Anthropic API 两种格式**，
--   按量付费的 OpenAI 兼容地址 = https://api.xiaomimimo.com/v1
--   ⚠️ Token Plan 订阅是**另一个地址 + 另一种 key 前缀**：
--      https://token-plan-cn.xiaomimimo.com/v1，key 形如 tp-xxxxx / ttp-xxxxx
--      （那种用户在界面上把 API 地址自己改掉即可，不必另建厂商）
--
-- 模型项**不预置**（2026-11-02 用户定）：填了密钥点「更新」现拉。
--   ⚠️ 小米的 `GET /v1/models` **存在**（2026-11-02 实测：返回 401 的 JSON，
--      不是 404）⇒ 刷新走的是通用 OpenAI 兼容那套，不按厂商分叉。
--   ⚠️ MiMo-V2.5 / V2.5-Pro 已于 2026-10-21 下线 —— 真要预置只能用 V2.6 系列。
--
-- ⚠️ **新库不用跑这个文件**：厂商清单的权威来源是 `2026-10-26_ai_vendor_catalog.sql`，
--    小米那行已经加进它 ⇒ 新库跑那两个文件就带上了。
--    **这个文件是给"已经存在的库"补的**（当前线上库就属于这种）。
--
-- 跑前自检 / 跑后校验 / 回滚段都在下面。
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 0) 跑前自检
-- ---------------------------------------------------------------------------
-- 0.1 这一行还没有（幂等守卫的依据）
SELECT id, code, name, remark, is_enabled, is_delete
FROM `sys_dict_type`
WHERE `code` = 'ai_vendor_xiaomi';
-- 期望：0 行

-- 0.2 现有厂商清单（跑后对账用）
SELECT id, code, name, remark, is_enabled
FROM `sys_dict_type`
WHERE `code` LIKE 'ai\_vendor\_%' AND `is_delete` = 0
ORDER BY id;
-- 期望：8 行（DeepSeek / 百炼 / 智谱 / Kimi / MiniMax / OpenAI / 硅基流动 / 火山方舟）

-- 0.3 确认 `sys_dict_type.code` 的唯一索引还在（幂等守卫靠它挡 Duplicate key）
SELECT COUNT(*) AS unique_idx
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'sys_dict_type'
  AND column_name = 'code' AND non_unique = 0;
-- 期望：1


-- ---------------------------------------------------------------------------
-- 1) 变更：插一行厂商类型
-- ---------------------------------------------------------------------------
-- ⚠️ **幂等**：`sys_dict_type.code` 有唯一索引 ⇒ 直接 INSERT 重跑会撞
--    Duplicate key。MySQL 的 INSERT 没有 IF NOT EXISTS ⇒ 用 NOT EXISTS 挡一次。
INSERT INTO `sys_dict_type` (`code`, `name`, `remark`, `is_enabled`)
SELECT 'ai_vendor_xiaomi', '小米 MiMo', 'https://api.xiaomimimo.com/v1', 1
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_dict_type` WHERE `code` = 'ai_vendor_xiaomi'
);

-- **模型项不预置** ⇒ 界面上选到这个厂商时模型下拉是空的，
-- 填了 API Key 点「更新」现拉（拉不到会报错，但**不会清空已有清单**）。
--
-- ⚠️ **不用改前端**：厂商下拉的选项来自后端 `GET /ai/vendor`
--    （按 `ai_vendor_` 前缀列字典类型），新增一行它自己就出现了。
--    ⚠️ **前端那份是模块级缓存** ⇒ 改完库要**刷新页面（F5）**才看得见。
--    ⚠️ **字典不过 Redis** ⇒ 不用重启后端。


-- ---------------------------------------------------------------------------
-- 跑后校验
-- ---------------------------------------------------------------------------
-- 1.1 这一行在了
SELECT id, code, name, remark, is_enabled, is_delete
FROM `sys_dict_type`
WHERE `code` = 'ai_vendor_xiaomi';
-- 期望：1 行，name=小米 MiMo，remark=https://api.xiaomimimo.com/v1，
--       is_enabled=1，is_delete=0

-- 1.2 厂商类型 9 个
SELECT COUNT(*) AS vendor_types
FROM `sys_dict_type`
WHERE `code` LIKE 'ai\_vendor\_%' AND `is_delete` = 0;
-- 期望：9

-- 1.3 小米下面没有预置模型项（用户定的口径：先不预置，靠「更新」现拉）
SELECT COUNT(*) AS xiaomi_models
FROM `sys_dict_item` i
JOIN `sys_dict_type` t ON t.id = i.dict_type_id
WHERE t.code = 'ai_vendor_xiaomi' AND i.is_delete = 0;
-- 期望：0

-- 1.4 别的厂商一个没动（对账）
SELECT id, code, name, remark
FROM `sys_dict_type`
WHERE `code` LIKE 'ai\_vendor\_%' AND `is_delete` = 0
ORDER BY id;
-- 期望：9 行，前 8 行与 0.2 的完全一致


-- ============================================================================
-- 回滚
-- ============================================================================
-- R1) 先删模型项（如果后来在界面上拉过清单，回滚要一并带走）
DELETE i FROM `sys_dict_item` i
JOIN `sys_dict_type` t ON t.id = i.dict_type_id
WHERE t.code = 'ai_vendor_xiaomi';

-- R2) 再删厂商类型
DELETE FROM `sys_dict_type` WHERE `code` = 'ai_vendor_xiaomi';
