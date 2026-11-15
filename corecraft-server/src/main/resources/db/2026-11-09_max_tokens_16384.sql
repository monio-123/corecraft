-- 2026-11-09 AI 单次输出上限 8192 → 16384（纯配置，免发版）
--
-- 背景（实测）：
--   后端请求体的 max_tokens 走三级优先级：
--   本次显式传 > 端点自定义参数 > sys_config 的 ai.chat.maxTokens > 常量 DEFAULT_MAX_TOKENS=8192。
--   本键此前**不存在** ⇒ 一直吃 8192 常量。
--
--   而 8192 被推理模型的思考吃掉大半：16 条对照实测 completion_tokens 开思考均 6,944、
--   最大 7,933（占 8192 的 96.8%），思考占 5,000~6,500 ⇒ 正文只剩约 1,200 token ≈ 1,800 中文字。
--
--   验证（tmp/reports/verify-16384-2026-11-09-2155.json，同提示词同材料、开思考、max_tokens=16384）：
--     · G1 收集器  completion_tokens = 9,821  正文 3,039 字 / 6 段  finish=stop
--       → 已远超 8192，若仍用 8192 这条**必然被截断**
--     · 安全点    7,933  / 1,613 字 / 4 段 / stop
--     · 类加载    6,090  / 1,750 字 / 4 段 / stop
--   最高 9,821 ⇒ 16384 留约 60% 余量，三条全部 finish=stop，0 截断。
--
-- 代价澄清：max_tokens 是**上限不是配额**，不截断时一个 token 都不会多花；
--   只在原本会截断的请求上才会继续生成（那本来就是该花完的）。
--   真实代价只有两条：① 模型跑飞时最坏多烧一倍；② 截断场景从「快但残缺」变成「慢但完整」。
--
-- 生效：getSystemValue 每次直查库、无本地缓存 ⇒ 改完立即生效，不用重启、不用发版。
-- 取值会被后端 clampTokens() 收进 [256, 32768]；写坏了退回 8192 常量，不报错。
--
-- 回滚：DELETE FROM corecraft.sys_config WHERE config_key='ai.chat.maxTokens' AND user_id=0;
--      （删掉即回落到常量 8192）

INSERT INTO corecraft.sys_config (user_id, config_key, config_value, remark)
SELECT 0, 'ai.chat.maxTokens', '16384',
       'AI 单次输出上限（token）。推理模型开思考时思考也占这份预算，8192 只够约 1200 正文 token ⇒ 提到 16384。范围 256~32768'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM corecraft.sys_config
  WHERE config_key = 'ai.chat.maxTokens' AND user_id = 0
);
