-- ============================================================================
-- 知识点正文提示词：结构化图补第三种形状 `pairs`（两组一一对应）
--
-- 日期：2026-11-12
-- 目标配置：sys_config / ai.prompt.knowledge.point（user_id = 0）
--
-- ## 为什么改
--
-- 判据里早就有「两组东西怎么一一对应」这一条（2026-11-09 加），提示词也自称
-- 「结构化图只有三种形状」，但**渲染层只实现了 `layers` / `stages` 两种 type**
-- （第三种是 `stages` 的布局分叉，不是一个 type）⇒ 模型碰到这类内容只能硬塞进
-- `layers`（一列框、每框一条）或 `stages`（编一个假的先后顺序），
-- 画出来读者看不出左右是配对的。
--
-- 前端侧同一个改动（同日）：`kbFig.js` 新增 `pairs` 渲染 + 形状自适应认领，
-- `kb-editor.css` 新增中轴虚线与结点样式。**两边必须同时到位** ——
-- 只改提示词 = 模型吐出画不出来的 type；只改渲染层 = 模型永远不吐 pairs。
--
-- ## 三段 REPLACE 各带 LIKE 守卫
--
-- 分三次改同一行会出现半新半旧的中间版本 ⇒ 三条按顺序跑，跑完一次性校验（文件末尾）。
--
-- 回滚：见文件末尾（三段各自的反向 REPLACE）。
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 【跑前校验】改前总长应在 4,900 上下
-- ---------------------------------------------------------------------------
SELECT
  CHAR_LENGTH(MAX(config_value))                                     AS 改前总长,
  SUM(config_value LIKE '%- **结构化图只有三种形状**%')              AS 段1标题,
  SUM(config_value LIKE '%- **结构化图的硬规矩**：标题 ≤ 14 字%')    AS 段2硬规矩,
  SUM(config_value LIKE '%`layers` ≤ 4 组、每组 items ≤ 5 个；%')    AS 段3上限
FROM `corecraft`.`sys_config`
WHERE `user_id` = 0 AND `config_key` = 'ai.prompt.knowledge.point';


-- ---------------------------------------------------------------------------
-- 【第 1 段】标题句：「只有三种形状（type 只能取 layers 或 stages）」→ 三个 type
--
-- ⚠️ 原文这句是**自相矛盾**的：说三种形状，却只准取两个 type
--    （第三种"stages + 侧翼"其实是同一个 type 的布局变体）⇒ 直接说明白。
-- ---------------------------------------------------------------------------
UPDATE `corecraft`.`sys_config`
SET `config_value` = REPLACE(
  `config_value`,
  '- **结构化图只有三种形状**（`type` 只能取 `layers` 或 `stages`，字段名照抄例子）：',
  '- **结构化图有三种形状**（`type` 取 `layers` / `stages` / `pairs`，字段名照抄例子）：'
)
WHERE `user_id` = 0
  AND `config_key` = 'ai.prompt.knowledge.point'
  AND `config_value` LIKE '%- **结构化图只有三种形状**%';


-- ---------------------------------------------------------------------------
-- 【第 2 段】在 `stages + 侧翼` 的例子之后、硬规矩之前，插入 `pairs`
--
-- ⚠️ 例子是这一段最重要的部分：模型模仿 JSON 结构的能力远强于理解文字描述
--    （2026-11-09 回测：11 条输出 kbfig、字段 100% 合规，靠的就是"字段名照抄例子"）。
-- ⚠️ 必须写清和表格的边界：pairs 画的是"谁对应谁"，两个对象逐项比取值仍然用表格
--    —— 否则提示词里「表格能对比清楚的不要再配图」会和 pairs 打架。
-- ---------------------------------------------------------------------------
UPDATE `corecraft`.`sys_config`
SET `config_value` = REPLACE(
  `config_value`,
  '- **结构化图的硬规矩**：标题 ≤ 14 字',
  '  - `pairs` —— **两组一一对应**：左边一个术语 / 一个码 / 一个策略，右边是**它的解释或后果**（例：原则 ↔ 解决的问题、状态码 ↔ 触发场景）。⚠️ **两个对象逐项比取值用表格、不用这个**（两种实现的延迟各是多少 —— 那是取值，不是对应关系）。`left` / `right` 是两个列名，**两个都要给**（只给一个等于一列表头）；每行 `l` 是左边、`r` 是右边：\n```kbfig\n{"type":"pairs","title":"SOLID 五原则","left":"原则","right":"解决的问题","rows":[{"l":"单一职责","r":"一个类只有一个变化理由"},{"l":"开闭原则","r":"加需求靠新增，不改已有代码"},{"l":"里氏替换","r":"子类能替换父类而不破坏调用方"}]}\n```\n- **结构化图的硬规矩**：标题 ≤ 14 字'
)
WHERE `user_id` = 0
  AND `config_key` = 'ai.prompt.knowledge.point'
  AND `config_value` LIKE '%- **结构化图的硬规矩**：标题 ≤ 14 字%';


-- ---------------------------------------------------------------------------
-- 【第 3 段】硬规矩里补 pairs 的数量上限
--
-- 渲染层的 LIMIT 比这里宽（rows 8 / 左边 20 字 / 右边 44 字）——**故意的**：
-- 提示词是"希望"，渲染层是"兜底"。超了会被静默截断成省略号（那段文本不会报错），
-- 所以提示词要卡得比渲染层更紧。
-- ---------------------------------------------------------------------------
UPDATE `corecraft`.`sys_config`
SET `config_value` = REPLACE(
  `config_value`,
  '`layers` ≤ 4 组、每组 items ≤ 5 个；',
  '`layers` ≤ 4 组、每组 items ≤ 5 个；`pairs` ≤ 6 行、左边 ≤ 10 字、右边 ≤ 20 字、`title`/`left`/`right` ≤ 8 字；'
)
WHERE `user_id` = 0
  AND `config_key` = 'ai.prompt.knowledge.point'
  AND `config_value` LIKE '%`layers` ≤ 4 组、每组 items ≤ 5 个；%';


-- ---------------------------------------------------------------------------
-- 【跑后校验】五项都应命中，总长从 ~4,971 涨到 ~5,500
-- ---------------------------------------------------------------------------
SELECT
  CHAR_LENGTH(MAX(config_value))                        AS 改后总长,
  SUM(config_value LIKE '%`pairs` —— **两组一一对应**%') AS pairs说明,
  SUM(config_value LIKE '%"type":"pairs"%')             AS pairs例子,
  SUM(config_value LIKE '%结构化图有三种形状%')          AS 标题已改,
  SUM(config_value LIKE '%结构化图只有三种形状%')        AS 旧标题残留,
  SUM(config_value LIKE '%`pairs` ≤ 6 行%')             AS pairs上限
FROM `corecraft`.`sys_config`
WHERE `user_id` = 0 AND `config_key` = 'ai.prompt.knowledge.point';


-- ---------------------------------------------------------------------------
-- 【回滚】三段反向 REPLACE（顺序无关，三段互不包含）
-- ---------------------------------------------------------------------------
-- UPDATE `corecraft`.`sys_config`
-- SET `config_value` = REPLACE(`config_value`,
--   '- **结构化图有三种形状**（`type` 取 `layers` / `stages` / `pairs`，字段名照抄例子）：',
--   '- **结构化图只有三种形状**（`type` 只能取 `layers` 或 `stages`，字段名照抄例子）：')
-- WHERE `user_id` = 0 AND `config_key` = 'ai.prompt.knowledge.point';
--
-- ⚠️ 第 2 段的回滚只能"删掉插入的那一段"（因为 REPLACE 只认原文锚点）：
--    把 '- **结构化图的硬规矩**：标题 ≤ 14 字' 之前那五行 pairs 文本整段替换成空串，
--    操作前先从 _backup 或本文件的【第 2 段】取出准确原文。
--
-- UPDATE `corecraft`.`sys_config`
-- SET `config_value` = REPLACE(`config_value`,
--   '`pairs` ≤ 6 行、左边 ≤ 10 字、右边 ≤ 20 字、`title`/`left`/`right` ≤ 8 字；', '')
-- WHERE `user_id` = 0 AND `config_key` = 'ai.prompt.knowledge.point';
