-- 配置树 + AI 提示词搬家
--
-- 【背景】
-- 「系统管理 → 系统配置」原来是一份定制清单（前端 utils/uiPrefs.js 的 PREF_GROUPS 写死
-- 有哪些组、每组有哪些项、叫什么、范围多大）。加一组配置 = 改代码 + 发版。
--
-- 这一步把它改成**通用的 KV 组浏览器**：
--   - 配置树（sys_config_node）= KV 的分组与说明书（名字 / 类型 / 范围 / 默认值）；
--   - 配置值（sys_config）= KV 的值；
--   - 树只是**展示用的目录**（"这个层级目录只是一个界面上便于展示的东西"），
--     不参与业务逻辑：行为那一层仍然是代码里的「键 → CSS 变量」。
--
-- 【键的构成】
--   config_key = 从根到本节点的 code 用 "." 连起来，例：
--     ui            （根节点，code = 命名空间）
--     ui.side       （侧边栏这组）
--     ui.side.sideWidth   ← 一个 KV
--     ai.prompt.question.rename:none  ← 一个 KV（提示词）
--   叶子节点的 code 就是它那一段的短名，所以"组名.项名"天然是键。
--
-- 【为什么要把 AI 提示词也搬进来】
--   原来提示词在字典表 sys_dict_item（remark 里）。字典表没有 user_id，
--   也没有"改一行就生效于全局"之外的分层；而提示词的本质就是**一段系统级配置文本**。
--   搬到 sys_config（user_id=0）之后，它和界面偏好是同一套东西：
--   管理员在同一个页面里改，改完不用发版。
--
--   键的对应关系：
--     字典 ai_topic_question 的 value（rename:none）→ ai.prompt.question.rename:none
--   （提示词正文里的【引用】写的还是短名，后端按前缀拼全键去查。）
--
--   ⚠️ **2026-10-21 改过两处**（现库的迁移脚本：_archive/2026-10-21_ai_prompt_global_system.sql）：
--     - **场景轴砍了**：字典 ai_system_prompt 迁过来的原本是 `ai.prompt.system.chat`
--       （一段问答口吻的描述，却发给了命名/归类/出题这些不是"提问"的任务）。
--       现在是一条「全局人设」，键 `ai.prompt.system`，**没有场景这一段了**；
--     - make_quiz 的措辞改成素材无关：素材不必是这条知识，也可以是用户粘的一段文字。
--       （2026-10-22：旧自测题整套撤了，**make_quiz 这条引导问题也没了** ——
--        出题由「考试」那一组提示词负责，见 2026-10-22_drop_old_quiz_feature.sql。）

-- ============================================================
-- 1. 配置值加长成 TEXT
-- ============================================================
-- AI 提示词最长 733 字，varchar(255) 装不下（塞进去就报错，截断就毁掉一段规则）。
ALTER TABLE `sys_config`
  MODIFY COLUMN `config_value` TEXT NOT NULL COMMENT '配置值（数字 / 字符串 / 长文本）';

-- ============================================================
-- 2. 配置树表
-- ============================================================
-- **它存的是"说明书"，不是值**：一个节点要么是一个分组（容器），要么是一个 KV（叶子）。
--   - 容器：value_type 为空，没有键值对；
--   - 叶子：value_type = number | text，值存在 sys_config 里。
--
-- **code 是短名，config_key 是全名**：
--   code       同一父下唯一（uk_config_key 通过全名间接保证）
--   config_key 全树唯一 = 根到本节点的 code 连起来
--   两者都不可改：改了键就变了，值会变成孤儿。
--   改名字、范围、默认值都可以（那几列不影响键）。
--
-- **default_value = 内置默认值**（数值项）。
--   生效值 = 我的覆盖 > 系统默认行 > 内置默认值。
--   这个值同时是 CSS 层那边的默认值（base.css / 各组件里的 var(--x, 兜底)），
--   所以**改内置默认值要两边一起改**——但只要前端写行内变量，
--   两边不一致时以这里为准（面板上显示的和渲染出来的一定是同一个数）。
CREATE TABLE IF NOT EXISTS `sys_config_node` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父节点；0=顶层',
  `name` varchar(64) NOT NULL COMMENT '显示名',
  `code` varchar(64) NOT NULL COMMENT '短名（同一父下唯一）',
  `config_key` varchar(128) NOT NULL COMMENT '完整配置键 = 从根到本节点的 code 用 . 连起来',
  `value_type` varchar(16) DEFAULT NULL COMMENT 'number | text；容器节点为空',

  `sort` int NOT NULL DEFAULT 0 COMMENT '同级排序',
  `remark` varchar(255) DEFAULT NULL COMMENT '说明（给人看的）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置树：KV 的分组与说明书';

-- ============================================================
-- 3. 灌树
-- ============================================================
-- 每层一条 INSERT ... SELECT，用「父的 config_key」关联；NOT EXISTS 保证重跑不重复灌。
-- ⚠️ 子查询里套一层 (SELECT * FROM ...) 是 MySQL 的老规矩：
--    直接在子查询里读正在写入的那张表会报 1093。

-- 3.1 顶层（两个命名空间：ui 界面配置 / ai AI 配置）
INSERT INTO `sys_config_node` (parent_id, name, code, config_key, sort, remark)
SELECT 0, n.name, n.code, n.code, n.sort, n.remark
FROM (
  SELECT '知识库系统界面配置' AS name, 'ui' AS code, 1 AS sort,
         '界面尺寸 / 间距 / 字号的系统默认' AS remark
  UNION ALL
  SELECT '系统 AI 配置', 'ai', 2, 'AI 接入与提示词'
) n
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `sys_config_node`) x WHERE x.parent_id = 0 AND x.code = n.code
);

-- 3.2 第二层：界面配置下的四组 + AI 配置下的「AI 提示词」
INSERT INTO `sys_config_node` (parent_id, name, code, config_key, sort, remark)
SELECT p.id, n.name, n.code, CONCAT(n.pkey, '.', n.code), n.sort, n.remark
FROM (
  SELECT 'ui' AS pkey, '侧边栏' AS name, 'side' AS code, 1 AS sort,
         '左侧栏的宽度与密度' AS remark
  UNION ALL SELECT 'ui', '首页', 'home', 2, '首页录入框与留白'
  UNION ALL SELECT 'ui', '知识点', 'learn', 3, '知识点列表页的间距'
  UNION ALL SELECT 'ui', '详情', 'detail', 4, '知识点详情页的间距与字号'
  UNION ALL SELECT 'ai', 'AI 提示词', 'prompt', 1, '喂给模型的那几段描述与规则'
) n
JOIN `sys_config_node` p ON p.config_key = n.pkey
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `sys_config_node`) x WHERE x.parent_id = p.id AND x.code = n.code
);

-- 3.3 第三层：AI 提示词下的「引导问题」
-- ⚠️ **原来这里还有一层「场景统一描述」**（2026-10-21 删掉）：
--   那个场景轴全站只传过 'chat' 一个值，而那段描述是问答口吻，
--   却被发给了命名 / 归类 / 出题这些不是"提问"的任务 ⇒ 假轴，砍掉。
--   全局人设现在是「AI 提示词」下的一个**叶子**（见 3.4），不再是第三层。
INSERT INTO `sys_config_node` (parent_id, name, code, config_key, sort, remark)
SELECT p.id, n.name, n.code, CONCAT(n.pkey, '.', n.code), n.sort, n.remark
FROM (
  SELECT 'ai.prompt' AS pkey, '引导问题' AS name, 'question' AS code, 2 AS sort,
         '抽屉空态那几个引导问题' AS remark
) n
JOIN `sys_config_node` p ON p.config_key = n.pkey
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `sys_config_node`) x WHERE x.parent_id = p.id AND x.code = n.code
);

-- 3.3.1 「考试」分组在**另一个脚本**里：2026-10-21_exam_prompt_config.sql
--   考试是 demo，要能整块拔掉 ⇒ 它的**节点和值放同一个脚本**，不在这里灌。
--   ⚠️ 两个脚本要一起跑：只有节点没有值，考试页出卷时拿不到规范
--      （后端拿不到提示词就退化成"只发一句『出卷』"，模型会自由发挥）。

-- 3.4 叶子：一个 KV 一行
--   值不在这里（值在 sys_config），这里只写"这一项长什么样"。
--   ⚠️ **表里没有"范围 / 步长 / 单位 / 内置默认"这几列**（2026-10-21 删掉）：
--   滑块范围是界面偏好专属的东西，塞进通用配置项就是耦合（用户："还有耦合问题"）。
--   **范围用一句人话写在「说明」里**（例："下限 200 是…"），结构化的那部分在
--   前端 uiPrefs.js 的 PREF_SPECS 里 —— 两边各管各的。
INSERT INTO `sys_config_node`
  (parent_id, name, code, config_key, value_type, sort, remark)
SELECT p.id, n.name, n.code, CONCAT(n.pkey, '.', n.code), n.vt, n.sort, n.remark
FROM (
  -- 界面配置：侧边栏
  SELECT 'ui.side' AS pkey, '宽度' AS name, 'sideWidth' AS code, 'number' AS vt,
         1 AS sort,
         '侧边栏宽度。下限 200 是「财务基本知识」这类目录名还基本放得下' AS remark
  UNION ALL SELECT 'ui.side', '左右', 'sidePadX', 'number', 2,
         '一行的左右内距'
  UNION ALL SELECT 'ui.side', '上下', 'sidePadY', 'number', 3,
         '一行的上下内距；同时写 --side-py 和 --side-gap（这两个本来就是同一个「一行有多松」）'
  -- 界面配置：首页
  UNION ALL SELECT 'ui.home', '录入框', 'homeQuick', 'number', 1,
         '录入框占「可分配高度」的几成。唯一的百分比项：调的不是长度，是剩下的高度怎么分'
  UNION ALL SELECT 'ui.home', '左右', 'homePadX', 'number', 2,
         '三排与左右侧端点的距离。往小调是主要用法（宽屏上两侧别空那么大）'
  UNION ALL SELECT 'ui.home', '上下', 'homeGap', 'number', 3,
         '三排之间的上下间距。下限 0：三排完全贴住也是一个合法排法'
  -- 界面配置：知识点
  UNION ALL SELECT 'ui.learn', '上下', 'learnGap', 'number', 1,
         '区块之间的间距'
  UNION ALL SELECT 'ui.learn', '左右', 'learnPadX', 'number', 2,
         '内容与左右端点的距离'
  UNION ALL SELECT 'ui.learn', '卡片间距', 'learnGridGap', 'number', 3,
         '卡片之间的间距'
  UNION ALL SELECT 'ui.learn', '筛选条', 'learnPillPy', 'number', 4,
         '筛选条药丸的上下内距。默认 3 不是 5：药丸是横着的，5 时接近正方形，挤在一排里比旁边重'
  -- 界面配置：详情
  -- ⚠️ **只有三项**（2026-10-21 用户定）：顶栏下 / 正文上方 / 正文左右 这三项
  -- **不再可配置**，值写死在代码里（CSS 的 token），配置树上不摆它们。
  -- 「顶栏下」= .bar 的 margin-bottom；「正文上方 / 正文左右」= 卡片的上下左右内距。
  -- **「正文白边」仍然可调**（它管文字离框边那条缝，和上面三项不是一回事）。
  -- 「阅读宽度」也没了（2026-10-21）：详情页不再有任何宽度封顶。
  UNION ALL SELECT 'ui.detail', '标签栏下', 'detailTagsGap', 'number', 1,
         '标签栏下方到正文框的距离（不是标签行内部 chip 之间的 gap）'
  UNION ALL SELECT 'ui.detail', '正文白边', 'detailTextInset', 'number', 2,
         '正文列左右各留多少。下限 20 是「箭头缩到 14px + 缝 6px」刚好装下的量；没拖过时走 CSS 的自动值（跟着字号缩放）'
  UNION ALL SELECT 'ui.detail', '正文字号', 'editorFs', 'number', 3,
         '正文字号。只写 --editor-fs 给编辑器读，不接 --text-md（那是全站的，接了整站跟着变大）'
  UNION ALL SELECT 'ui.detail', '问答宽', 'detailAskW', 'number', 4,
         'AI 问答抽屉的宽度。JS 值不是 CSS 变量（el-drawer 的 size 会把 var() 拼成非法宽度）'
  -- AI：全局人设（**直接挂在「AI 提示词」下**，2026-10-21）
  UNION ALL SELECT 'ai.prompt', '全局人设', 'system', 'text', 1,
         '所有任务共用的一段系统描述：你是谁、回答风格、不许编。任务规则在引导问题里'
  -- AI：引导问题
  UNION ALL SELECT 'ai.prompt.question', '给这个知识点命名', 'rename:none', 'text', 1,
         '给一条还没起名字的知识起名'
  UNION ALL SELECT 'ai.prompt.question', '要关联哪个目录', 'pick_category:categories', 'text', 2,
         '选主归属目录。附料：现有目录清单'
  UNION ALL SELECT 'ai.prompt.question', '应该关联哪些标签', 'pick_tags:tags', 'text', 3,
         '选主归属之外涉及的标签。附料：现有标签清单'
  -- ⚠️ **这里原来还有一条「生成几道测验题」（make_quiz:none）**，2026-10-22 连旧自测题
  --    整套一起撤了（题、记录、每日复习、详情页入口）⇒ 引导问题里不再有它。
  --    出题这件事现在由「考试」那一组提示词负责（ai.prompt.exam.*）。
  UNION ALL SELECT 'ai.prompt.question', '梳理一下这个知识的重点', 'comb:none', 'text', 4,
         '判断这条知识是否正确、梳理它的重点'
  UNION ALL SELECT 'ai.prompt.question', '补全这条知识（标题 / 目录 / 标签）', 'organize:categories,tags', 'text', 5,
         '一次给全标题 / 目录 / 标签。规则用【引用】引上面那几条'
) n
JOIN `sys_config_node` p ON p.config_key = n.pkey
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `sys_config_node`) x WHERE x.parent_id = p.id AND x.code = n.code
);

-- ============================================================
-- 4. AI 提示词进 sys_config
-- ============================================================
-- 值搬进 sys_config，键按上面的规则拼。

-- 4.1 全局人设：**不再从字典迁**（2026-10-21）。
-- 原来迁过来的是 `ai.prompt.system.chat`（一段问答口吻的描述，却发给了
-- 命名 / 归类 / 出题这些不是"提问"的任务）。场景轴砍掉后，这段文字换成了
-- 「全局人设」，键也变成 ai.prompt.system ⇒ 字典里那份直接作废。
-- ⚠️ **这段文字在 _archive/2026-10-21_ai_prompt_global_system.sql 里也有一份**
--    （那份是给现库跑的一次性迁移）—— 改措辞两个都要改。
INSERT INTO `sys_config` (user_id, config_key, config_value, remark)
VALUES (0, 'ai.prompt.system',
  '你是一个学习助手，服务于一个自主学习平台：用户在这里记录和整理知识点，给知识点归类、打标签，基于知识点出练习题，也会就某个知识点的内容向你提问。
你的回答只依据这次给你的内容，不要拿你记忆里的版本替换它；内容里没写到的地方，明说「这份内容里没写」，不要自己补。
直接给结果：不要复述任务、不要解释你正在做什么、不要写「好的」这类开场白。',
  '全局人设：所有任务共用的一段系统描述')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), remark = VALUES(remark);

-- 4.2 引导问题：从字典迁（键 = ai.prompt.question.<短名>）
-- ON DUPLICATE KEY UPDATE：重跑时用字典里的值覆盖，不留第一次的旧值。
-- ⚠️ **make_quiz 排除在外**（2026-10-22）：旧自测题整套撤了，那条引导问题也一并没了
--    —— 不排除的话，重放这个脚本会把它从字典里又迁回来。
INSERT INTO `sys_config` (user_id, config_key, config_value, remark)
SELECT 0, CONCAT('ai.prompt.question.', i.value), i.remark, '由字典 ai_topic_question 迁移'
FROM `sys_dict_item` i
JOIN `sys_dict_type` t ON t.id = i.dict_type_id AND t.code = 'ai_topic_question'
WHERE i.is_delete = 0
  AND i.value NOT LIKE 'make_quiz%'
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), remark = VALUES(remark);

-- ============================================================
-- 5. 字典里那批提示词项下线
-- ============================================================
-- **逻辑删**（is_delete=1），不是物理删：出错了还能捞回来。
-- 这两个字典类型下线后就没有消费者了（唯一读它们的是 AiChatServiceImpl，
-- 现在改读 sys_config）；字典管理页是通用的，会自动不再显示它们。
-- ⚠️ kp_topic_type（知识点类型）**不动** —— 业务还在用。

UPDATE `sys_dict_item`
SET is_delete = 1
WHERE is_delete = 0
  AND dict_type_id IN (SELECT id FROM `sys_dict_type` WHERE code IN ('ai_system_prompt', 'ai_topic_question'));

UPDATE `sys_dict_type`
SET is_delete = 1
WHERE is_delete = 0
  AND code IN ('ai_system_prompt', 'ai_topic_question');
