-- 提示词加「补充说明」：生成知识包弹窗的第二个输入框（用户自己写的一句话，如「面向开发」「面向考试」）
-- 2026-10-26
--
-- 背景：生成弹窗多了一个「补充说明」框，它会进材料（`buildMaterial` / outline 的材料）。
--   **光把文字塞进材料不够** —— 提示词逐条列了材料里有什么（"材料里给了：**主题**、**目录路径**…"），
--   没说有"补充说明"这一项，模型就当它是闲话 ⇒ 每一批都得在提示词里提一句。
--
-- 改两处（都只动一段，其它一个字不动）：
--   ① `ai.prompt.knowledge.outline` —— 骨架也按补充说明取舍（面向考试 ⇒ 章节偏考点）
--   ② `ai.prompt.knowledge.point`  —— 每一条正文都按补充说明取舍
--
-- **这是配置不是表结构**：`sys_config.config_value` 一改即时生效，不用发版。
-- 改的是**系统级**提示词（user_id = 0）⇒ 所有人生效。
--
-- 跑前建议（内容级还原）：
--   mysqldump -uroot -p<pwd> corecraft sys_config \
--     --where="user_id=0 AND config_key IN ('ai.prompt.knowledge.outline','ai.prompt.knowledge.point')" \
--     > <工作区外>/corecraft_pre_extra_note_20261026.sql
--
-- 执行时机：随时（只动 corecraft 库）。


-- ========== ① 跑前自检（两条都要命中 1）============
SELECT config_key, CHAR_LENGTH(config_value) AS 长度,
       (config_value LIKE '%材料里给了**主题**，可能还有**现有目录清单**。%') AS outline_待改处,
       (config_value LIKE '%可能还给了**这次要写的知识点标题清单**%') AS point_待改处
  FROM corecraft.sys_config
 WHERE user_id = 0
   AND config_key IN ('ai.prompt.knowledge.outline', 'ai.prompt.knowledge.point');


-- ========== ② 改 ==========
-- 骨架：把「补充说明」列进材料清单，并给它一条判据
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         '材料里给了**主题**，可能还有**现有目录清单**。',
         '材料里给了**主题**，可能还有**补充说明**和**现有目录清单**。\n- **补充说明**（用户自己写的一句话，如「面向开发」「面向考试」）是这一批的额外要求：按它取舍内容重点——面向开发就偏工程实现与排障，面向考试就偏考点与易错点。'
       ),
       update_time = NOW()
 WHERE user_id = 0
   AND config_key = 'ai.prompt.knowledge.outline';

-- 正文：同样列进材料清单，并在第一段后面补一条判据
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         REPLACE(
           config_value,
           '可能还给了**这次要写的知识点标题清单**',
           '可能还给了**补充说明**、**这次要写的知识点标题清单**'
         ),
         '（总览知识点要用；章节后面带地址的，把章节名写成链接）。',
         '（总览知识点要用；章节后面带地址的，把章节名写成链接）。\n\n**补充说明**（用户自己写的一句话，如「面向开发」「面向考试」）是这一批的额外要求：**每一条正文都按它取舍**——面向开发就多写工程实现、排障与注意事项，面向考试就多写考点与易错点。'
       ),
       update_time = NOW()
 WHERE user_id = 0
   AND config_key = 'ai.prompt.knowledge.point';


-- ========== ③ 跑后校验（两条都要命中 1 / 1）============
SELECT config_key, CHAR_LENGTH(config_value) AS 长度,
       (config_value LIKE '%**补充说明**%') AS 有补充说明,
       (config_value LIKE '%补充说明%') AS 出现次数
  FROM corecraft.sys_config
 WHERE user_id = 0
   AND config_key IN ('ai.prompt.knowledge.outline', 'ai.prompt.knowledge.point');


-- ========== 回滚 ==========
-- UPDATE corecraft.sys_config
--    SET config_value = REPLACE(
--          REPLACE(
--            config_value,
--            '材料里给了**主题**，可能还有**补充说明**和**现有目录清单**。\n- **补充说明**（用户自己写的一句话，如「面向开发」「面向考试」）是这一批的额外要求：按它取舍内容重点——面向开发就偏工程实现与排障，面向考试就偏考点与易错点。',
--            '材料里给了**主题**，可能还有**现有目录清单**。'
--          ),
--          '可能还给了**补充说明**、**这次要写的知识点标题清单**',
--          '可能还给了**这次要写的知识点标题清单**'
--        ),
--        update_time = NOW()
--  WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.outline';
--
-- UPDATE corecraft.sys_config
--    SET config_value = REPLACE(
--          REPLACE(
--            config_value,
--            '（总览知识点要用；章节后面带地址的，把章节名写成链接）。\n\n**补充说明**（用户自己写的一句话，如「面向开发」「面向考试」）是这一批的额外要求：**每一条正文都按它取舍**——面向开发就多写工程实现、排障与注意事项，面向考试就多写考点与易错点。',
--            '（总览知识点要用；章节后面带地址的，把章节名写成链接）。'
--          ),
--          '可能还给了**补充说明**、**这次要写的知识点标题清单**',
--          '可能还给了**这次要写的知识点标题清单**'
--        ),
--        update_time = NOW()
--  WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.point';
