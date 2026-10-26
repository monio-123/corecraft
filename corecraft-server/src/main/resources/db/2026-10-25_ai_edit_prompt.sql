-- ============================================================
-- 2026-10-25 AI 调整知识点正文：新增一组提示词 + 一个偏好项
--
-- ## 为什么加
--
-- 知识点详情页要能**用一句自然语言让 AI 改写正文**
-- （「新增一节讲 xxx」「在某个知识点里举个例子」）。
--
-- **后端零改动**：前端把「用户指令 + 当前正文」拼成 `material` 一起发过去，
-- 后端 `AiChatServiceImpl.buildMessages` 给了 material 就不再去捞库里的正文
-- ⇒ 给 AI 的是**屏幕上那一版**（含还没保存的编辑）。
--
-- 提示词落在**完整键** `ai.prompt.edit.body` 下，不在 `ai.prompt.question.` 下面：
-- 后端 `promptKeyOf` 按「有没有点」区分（带点 = 完整键，原样用）。
-- 放在 `ai.prompt.question.` 下面的话，它会被 `GET /ai/chat/questions`
-- 当成一条引导问题 ⇒ **混进问答抽屉的按钮里**。
-- 这条路和已有的 `ai.prompt.knowledge.*` 完全同构。
--
-- ## 为什么提示词里要写死「用两个标记包起来」
--
-- 前端要把 AI 的输出**原样当成正文**写回编辑器。
-- 模型爱在正文外面加开场白（「好的，以下是改写后的内容：」）或者
-- 把整篇塞进 ``` 代码块 —— 那些进了编辑器就是正文的一部分，
-- 界面上看不出异常，只是「AI 写得不对」。
--
-- 所以提示词要求**第一行 【正文开始】、最后一行 【正文结束】**，
-- 前端按标记取（见 corecraft-web/src/views/corecraft-web/aiEdit.js）。
-- 解析不出来就不给「应用到正文」按钮，只给复制 —— 与 aiAdopt.js 同一口径：
-- **写进库比不写更糟**。
--
-- ## 顺带加一个偏好项
--
-- `ui.detail.detailEditW`：AI 调整正文抽屉的宽度。
-- **它是 JS 值不是 CSS 变量**（el-drawer 的 size 会给 var() 拼 px 后缀，
-- 变成非法宽度，抽屉直接打不开）—— 与 `ui.detail.detailAskW` 同一件事。
--
-- 为什么要单独一项而不是复用问答抽屉的宽度：两边形态不同。
-- 问答抽屉是一问一答，420px 够；调整正文抽屉是**左右两栏并排对比**，
-- 420px 分两半每栏只剩 210px，根本读不下去 ⇒ 默认给 720。
--
-- ⚠️ **不给它写 user_id=0 的系统默认值行**：内置默认在代码里
-- （uiPrefs.js 的 PREF_SPECS），`detailAskW` 也没有那一行 ——
-- 加了等于把内置默认复制一份到库里，改代码就不会生效了。
--
-- ============================================================

-- ------------------------------------------------------------
-- 1. 配置树：AI 提示词下面加一组「正文调整」
-- ------------------------------------------------------------

-- 1.1 分类节点（value_type 为空 = 它是分类，不是配置项）
INSERT INTO `sys_config_node`
  (`parent_id`, `name`, `code`, `config_key`, `value_type`, `sort`, `remark`)
SELECT
  p.id,
  '正文调整',
  'edit',
  'ai.prompt.edit',
  NULL,
  5,
  '用自然语言指令改写知识点正文'
FROM `sys_config_node` p
WHERE p.config_key = 'ai.prompt'
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT id FROM `sys_config_node` WHERE config_key = 'ai.prompt.edit') x
  );

-- 1.2 配置项：改写规范
INSERT INTO `sys_config_node`
  (`parent_id`, `name`, `code`, `config_key`, `value_type`, `sort`, `remark`)
SELECT
  p.id,
  '改写规范',
  'body',
  'ai.prompt.edit.body',
  'text',
  1,
  '按指令改写正文时整份发给模型。输出必须是**改写后的完整正文**，前后用【正文开始】/【正文结束】包起来'
FROM `sys_config_node` p
WHERE p.config_key = 'ai.prompt.edit'
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT id FROM `sys_config_node` WHERE config_key = 'ai.prompt.edit.body') x
  );

-- ------------------------------------------------------------
-- 2. 提示词正文（系统默认，user_id = 0）
-- ------------------------------------------------------------

INSERT INTO `sys_config`
  (`user_id`, `config_key`, `config_value`, `remark`)
SELECT
  0,
  'ai.prompt.edit.body',
  '按用户的要求改写这份正文。\n\n材料里给了两段：\n- 【调整要求】：用户这次要你做什么（新增一节 / 在某处举个例子 / 精简 / 扩写 / 修正错误…）\n- 【原文】：要改写的正文本体\n\n**只输出改写后的完整正文**（整篇，不只是改动的那部分），前后各加一行标记：\n- 第一行写 【正文开始】\n- 最后一行写 【正文结束】\n\n标记之间就是改写后的完整正文。标记之外一个字都不写：不要开场白、不要解释改了什么、不要结尾总结。\n\n改写规则：\n- **没要求改的部分原样保留**。用户只让新增一节，就别顺手把别的段落也重写。\n- **结构跟着原文走**：原文的标题层级、编号、段落划分照抄；新增的小标题接在原文的编号后面继续排。\n- **改动直接写进正文**，不要写「（此处新增）」这种占位，也不要单独列一份改动清单。\n- **原文里已经写了的结论以原文为准**，不要拿你记忆里的版本替换它。\n- **用户要求新增或扩写的部分，按你自己的知识写**（材料只给了原文，新增的内容材料里当然没有）。\n- **标题（# / ##）原样保留**，除非用户明确要求改标题。\n- 正文里的 markdown 照常用（标题、列表、表格、代码块），但**不要把整篇正文包进代码块**。\n- 写不出来的地方宁可不写，也不要编造事实。',
  '按指令改写正文时整份发给模型。输出用【正文开始】/【正文结束】包住改写后的完整正文'
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT id FROM `sys_config` WHERE user_id = 0 AND config_key = 'ai.prompt.edit.body') x
);

-- ------------------------------------------------------------
-- 3. 偏好项：AI 调整正文抽屉的宽度（JS 值，不进偏好面板的滑块）
-- ------------------------------------------------------------

INSERT INTO `sys_config_node`
  (`parent_id`, `name`, `code`, `config_key`, `value_type`, `sort`, `remark`)
SELECT
  p.id,
  '编辑宽',
  'detailEditW',
  'ui.detail.detailEditW',
  'number',
  10,
  'AI 调整正文抽屉的宽度。左右两栏并排对比，窄了看不清；JS 值不是 CSS 变量'
FROM `sys_config_node` p
WHERE p.config_key = 'ui.detail'
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT id FROM `sys_config_node` WHERE config_key = 'ui.detail.detailEditW') x
  );


-- ============================================================
-- 回滚
-- ============================================================

-- R1. 提示词正文
DELETE FROM `sys_config`
WHERE `user_id` = 0 AND `config_key` = 'ai.prompt.edit.body';

-- R2. 配置树节点（先子后父，父节点靠子节点的下级关系定位不了，直接按 config_key 删）
DELETE FROM `sys_config_node` WHERE `config_key` = 'ai.prompt.edit.body';
DELETE FROM `sys_config_node` WHERE `config_key` = 'ui.detail.detailEditW';
DELETE FROM `sys_config_node` WHERE `config_key` = 'ai.prompt.edit';
