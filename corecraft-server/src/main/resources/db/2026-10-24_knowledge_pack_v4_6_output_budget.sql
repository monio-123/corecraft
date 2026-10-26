-- 知识点正文提示词 v4.6：收窄单条字数 + "宁可少写几条，不要写半条"
-- 2026-10-24
--
-- 背景：用户重新生成两棵树时，「8.3 政府会计报告」那一次**生成报错了**（流里回了
--   error），整个目录空着 ⇒ 用户要"下次生成不要再有这个问题"。
--
-- ⚠️ **报错本身是接口侧的瞬时错误（限流 / 断流 / 超时），提示词改不掉它** ——
--   那一侧已经补了**自动重试一次**（前端 `CategoryTreeDialog.vue` 的 `askWithRetry`）。
--   提示词能减少的是**另一半：单次输出量太大 ⇒ 流式时间太长 ⇒ 更容易超时/断流**。
--
-- 依据（实测数据，不是感觉）：
--   · 提示词要求"500 到 900 字"，**实测每条正文写到 1441-1948 字**（v4 复验时的量）
--     ⇒ 一个目录 3-5 条 = 5000-9000 字 + 表格/列表，单次输出接近万级 token
--   · 流式生成的时间与输出量成正比 ⇒ **超时/断流的概率被输出量放大**
--   ⇒ 把上限从 900 收到 700，并明确"宁可少写几条" ⇒ 输出量减半，
--     而"少写几条"是**安全降级**（解析器按标题对齐，少的那条会明确报"少 N 条"），
--     "写半条"不是（它会变成残缺正文）
--
-- 改法：**两处 REPLACE**（不重打整段提示词 ⇒ 脚本可重放、diff 最小）
--
-- 执行时机：随时（只动 corecraft 库）。


-- ========== ① 跑前自检（应当输出 1 行，长度 1638 = v4.5）============
SELECT id, config_key, CHAR_LENGTH(config_value) AS len
  FROM corecraft.sys_config
 WHERE config_key = 'ai.prompt.knowledge.point';


-- ========== ② 改成 v4.6 ==========
-- ⚠️ **拼接必须用 CONCAT，不能用 `||`**：MySQL 默认 sql_mode 下 `||` 是**逻辑 OR**
--    （不是字符串拼接）⇒ `'...' || CHAR(10)` 会被当成把中文串转 DOUBLE，直接报错
--    "Truncated incorrect DOUBLE value"。这台库的 PIPES_AS_CONCAT 没开。
UPDATE corecraft.sys_config
SET config_value = REPLACE(
      REPLACE(
        config_value,
        '- **正文是 markdown，500 到 900 字**',
        '- **正文是 markdown，500 到 700 字**（一条知识点的总长，别超）'
      ),
      CONCAT('**五段都要写**，写不出来就写你确实知道的那部分，不要留空', CHAR(10)),
      CONCAT('**五段都要写**，写不出来就写你确实知道的那部分，不要留空', CHAR(10),
             '- **一次只写这次要写的那几条，写不完宁可少写几条，也不要写半条**（写半条会被解析成残缺正文；少写的那几条会被明确报出来）', CHAR(10))
    )
WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.point';


-- ========== ③ 跑后校验 ==========
SELECT
  CHAR_LENGTH(config_value)                                    AS len,
  config_value LIKE '%500 到 700 字%'                          AS 收到700,
  config_value LIKE '%500 到 900 字%'                          AS 旧字数还在,
  config_value LIKE '%宁可少写几条，也不要写半条%'              AS 少写规则,
  config_value LIKE '%**代码块**%'                             AS 保留代码块,
  config_value LIKE '%不要写图片和流程图%'                      AS 保留禁图,
  config_value LIKE '%五段都要写%'                             AS 保留五段
  FROM corecraft.sys_config
 WHERE config_key = 'ai.prompt.knowledge.point';


-- ========== 回滚（回到 v4.5）============
-- UPDATE corecraft.sys_config
-- SET config_value = REPLACE(
--       REPLACE(
--         REPLACE(
--           config_value,
--           '- **正文是 markdown，500 到 700 字**（一条知识点的总长，别超）',
--           '- **正文是 markdown，500 到 900 字**'
--         ),
--         CONCAT('- **一次只写这次要写的那几条，写不完宁可少写几条，也不要写半条**（写半条会被解析成残缺正文；少写的那几条会被明确报出来）', CHAR(10)),
--         ''
--       ),
--       CONCAT(CHAR(10), '- **正文是 markdown，500 到 700 字**（一条知识点的总长，别超）'),
--       '- **正文是 markdown，500 到 700 字**（一条知识点的总长，别超）'
--     )
-- WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.point';
--
-- （回滚段的 REPLACE 顺序与执行段相反：先删新增的那一行，再把 700 改回 900。
--   末尾那个 REPLACE 是去掉行首多出来的换行。）
