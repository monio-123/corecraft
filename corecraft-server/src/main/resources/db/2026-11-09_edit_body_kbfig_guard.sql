-- ============================================================================
-- AI 改写正文（「改出来」）提示词：加一条图保护
--
-- 日期：2026-11-09
-- 目标配置：sys_config / ai.prompt.edit.body（user_id = 0）
--
-- ## 为什么改
--
-- 同一批代码（#813）给生成侧加了结构化图（```kbfig）。但「改出来」是**另一条链路**：
-- 用户让 AI 调整某一段正文时，AI 会看到整个正文，其中包含 kbfig / mermaid 代码块。
-- 它会「好心」地把 JSON 重排、把字段名改成自己觉得更顺的写法，或者把 mermaid 语法
-- 「优化」成它熟悉的版本 —— 结果就是图直接画不出来（解析失败）。
--
-- 成本一句话，漏了会埋雷：用户以为 AI 把正文改好了，实际把图改坏了，而且不报错。
--
-- 生效时机：改完立即生效，下次「改出来」就吃到。
-- ============================================================================


-- 【跑前校验】应为 1
SELECT SUM(config_value LIKE '%- 正文里的 markdown 照常用%') AS 命中,
       CHAR_LENGTH(MAX(config_value))                      AS 改前总长
FROM `corecraft`.`sys_config`
WHERE `user_id` = 0 AND `config_key` = 'ai.prompt.edit.body';


-- 【改动】在「markdown 照常用」那条后面插入图保护
UPDATE `corecraft`.`sys_config`
SET `config_value` = REPLACE(
  `config_value`,
  '- 正文里的 markdown 照常用（标题、列表、表格、代码块），但**不要把整篇正文包进代码块**。',
  '- 正文里的 markdown 照常用（标题、列表、表格、代码块），但**不要把整篇正文包进代码块**。\n- **图不要重排**：```kbfig 和 ```mermaid 代码块里的内容，只改**字段的文字值**（比如某个步骤名、某句说明）；**不许改 JSON 的结构、字段名、type，也不许「优化」mermaid 的语法**。用户没要求改图，就原样保留。'
)
WHERE `user_id` = 0
  AND `config_key` = 'ai.prompt.edit.body'
  AND `config_value` LIKE '%- 正文里的 markdown 照常用%';


-- 【跑后校验】两项都应为 1
SELECT SUM(config_value LIKE '%图不要重排%') AS 图保护,
       SUM(config_value LIKE '%不许改 JSON 的结构%') AS 字段保护,
       CHAR_LENGTH(MAX(config_value))              AS 改后总长
FROM `corecraft`.`sys_config`
WHERE `user_id` = 0 AND `config_key` = 'ai.prompt.edit.body';


-- 【回滚】
-- UPDATE `corecraft`.`sys_config`
-- SET `config_value` = REPLACE(`config_value`,
--   '\n- **图不要重排**：```kbfig 和 ```mermaid 代码块里的内容，只改**字段的文字值**（比如某个步骤名、某句说明）；**不许改 JSON 的结构、字段名、type，也不许「优化」mermaid 的语法**。用户没要求改图，就原样保留。',
--   '')
-- WHERE `user_id` = 0 AND `config_key` = 'ai.prompt.edit.body';
